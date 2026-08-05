package com.example.partitionswap.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The zero-downtime swap itself: explicit SQL, executed in autocommit, never inside a
 * Spring-managed transaction.
 *
 * <p>Lifecycle of one minute partition:
 * <pre>
 *   events_ingest child  --DETACH CONCURRENTLY-->  standalone table
 *   standalone table     --CREATE INDEX x2------>  indexed standalone table
 *   indexed table        --ADD CHECK + ATTACH--->  events child (attach is metadata-only)
 * </pre>
 *
 * <p>Why each step is non-blocking for the producer and consumer:
 * <ul>
 *   <li>{@code DETACH PARTITION ... CONCURRENTLY} takes only SHARE UPDATE EXCLUSIVE on
 *       the parent — concurrent INSERT/SELECT proceed. It must run outside a transaction
 *       block, which is why nothing here is {@code @Transactional}.</li>
 *   <li>The index builds happen on a detached, no-longer-written table; nobody is
 *       blocked because nobody else touches it.</li>
 *   <li>The pre-added CHECK constraint proves the range to PostgreSQL, so
 *       {@code ATTACH PARTITION} skips its validation scan and holds its brief
 *       SHARE UPDATE EXCLUSIVE lock on {@code events} for microseconds.</li>
 * </ul>
 *
 * <p>Crash safety: every step is idempotent or resumable. A DETACH CONCURRENTLY that
 * died between its two internal transactions leaves the child flagged
 * {@code inhdetachpending} — the next run issues {@code DETACH ... FINALIZE}. A crash
 * after detach but before attach leaves a standalone {@code events_p*} table —
 * {@link #adoptOrphans()} finds it via the catalog and finishes the swap.
 */
@Slf4j
@Component
public class PartitionSwapService {

    private final JdbcClient jdbc;
    private final Duration grace;
    private final int minutesAhead;
    private final Duration retention;

    public PartitionSwapService(JdbcClient jdbc,
                                @Value("${demo.swap.grace-seconds:2}") long graceSeconds,
                                @Value("${demo.swap.minutes-ahead:3}") int minutesAhead,
                                @Value("${demo.swap.retention-minutes:30}") long retentionMinutes) {
        this.jdbc = jdbc;
        this.grace = Duration.ofSeconds(graceSeconds);
        this.minutesAhead = minutesAhead;
        this.retention = Duration.ofMinutes(retentionMinutes);
    }

    /** A direct child of a partitioned parent, plus its half-detached marker. */
    public record ChildPartition(String name, boolean detachPending) {
    }

    /**
     * Pre-creates minute partitions on the ingest side, from one minute in the past
     * through {@code minutesAhead} minutes ahead, so row routing never races partition
     * creation. {@code IF NOT EXISTS} makes this safe to call every minute.
     */
    public void ensureIngestPartitions(Instant asOf) {
        Instant minute = PartitionNaming.minuteOf(asOf).minus(PartitionNaming.WIDTH);
        Instant last = PartitionNaming.minuteOf(asOf).plus(PartitionNaming.WIDTH.multipliedBy(minutesAhead));
        while (!minute.isAfter(last)) {
            String name = PartitionNaming.nameFor(minute);
            ddl("CREATE TABLE IF NOT EXISTS %s PARTITION OF events_ingest FOR VALUES FROM (%s) TO (%s)"
                    .formatted(name,
                            PartitionNaming.boundLiteral(minute),
                            PartitionNaming.boundLiteral(minute.plus(PartitionNaming.WIDTH))));
            minute = minute.plus(PartitionNaming.WIDTH);
        }
    }

    /**
     * Swaps every ingest partition whose minute has fully elapsed (plus a small grace
     * window for in-flight commits) over to the read table. Returns the swapped names.
     */
    public List<String> swapCompletedPartitions(Instant asOf) {
        Instant cutoff = asOf.minus(grace);
        List<String> swapped = new ArrayList<>();

        for (ChildPartition child : childrenOf("events_ingest")) {
            Instant upper = PartitionNaming.upperBoundOf(child.name());
            if (child.detachPending()) {
                // A previous DETACH CONCURRENTLY died between its two transactions.
                log.warn("Partition {} left detach-pending by an earlier run — finalizing", child.name());
                ddl("ALTER TABLE events_ingest DETACH PARTITION %s FINALIZE".formatted(child.name()));
            } else if (upper.isAfter(cutoff)) {
                continue; // minute still open (or within the grace window)
            } else {
                long rows = countRows(child.name());
                log.info("Swapping partition {} ({} rows) out of events_ingest", child.name(), rows);
                // Runs as two internal transactions; must NOT be inside a transaction block.
                ddl("ALTER TABLE events_ingest DETACH PARTITION %s CONCURRENTLY".formatted(child.name()));
            }
            indexAndAttach(child.name());
            swapped.add(child.name());
        }
        return swapped;
    }

    /**
     * Finds standalone {@code events_p*} tables — detached from ingest but never attached
     * to the read table because a previous run crashed in between — and finishes their swap.
     */
    public List<String> adoptOrphans() {
        List<String> orphans = jdbc.sql("""
                        SELECT c.relname
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = current_schema()
                          AND c.relkind = 'r'
                          AND c.relname LIKE 'events\\_p%'
                          AND NOT EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid)
                        ORDER BY c.relname""")
                .query(String.class)
                .list();
        for (String orphan : orphans) {
            log.warn("Adopting orphaned partition {} (detached but never attached)", orphan);
            indexAndAttach(orphan);
        }
        return orphans;
    }

    /**
     * Drops read-table partitions older than the retention window. DETACH CONCURRENTLY
     * first, so readers scanning {@code events} are never blocked; the DROP then only
     * touches a standalone table.
     */
    public List<String> enforceRetention(Instant asOf) {
        Instant horizon = asOf.minus(retention);
        List<String> dropped = new ArrayList<>();
        for (ChildPartition child : childrenOf("events")) {
            if (child.detachPending()) {
                ddl("ALTER TABLE events DETACH PARTITION %s FINALIZE".formatted(child.name()));
            } else if (PartitionNaming.upperBoundOf(child.name()).isAfter(horizon)) {
                continue;
            } else {
                log.info("Retention: dropping partition {} (older than {})", child.name(), retention);
                ddl("ALTER TABLE events DETACH PARTITION %s CONCURRENTLY".formatted(child.name()));
            }
            ddl("DROP TABLE IF EXISTS %s".formatted(child.name()));
            dropped.add(child.name());
        }
        return dropped;
    }

    /**
     * Builds the read-optimized indexes on a detached partition, then attaches it to
     * {@code events}. Index names embed the partition name so they are unique, and their
     * definitions match the partitioned indexes on the parent — ATTACH links them to the
     * parent indexes instead of building anything under lock.
     */
    private void indexAndAttach(String partition) {
        Instant lower = PartitionNaming.lowerBoundOf(partition);
        Instant upper = PartitionNaming.upperBoundOf(partition);
        String from = PartitionNaming.boundLiteral(lower);
        String to = PartitionNaming.boundLiteral(upper);
        String check = partition + "_bounds_check";

        long started = System.nanoTime();
        ddl("CREATE INDEX IF NOT EXISTS %s_type_time_idx ON %s (event_type, occurred_at)"
                .formatted(partition, partition));
        ddl("CREATE INDEX IF NOT EXISTS %s_time_idx ON %s (occurred_at)"
                .formatted(partition, partition));

        // Proves the partition's range up front so ATTACH PARTITION skips its full-table
        // validation scan. The scan happens here instead — on a table nobody writes to.
        ddl("ALTER TABLE %s DROP CONSTRAINT IF EXISTS %s".formatted(partition, check));
        ddl("ALTER TABLE %s ADD CONSTRAINT %s CHECK (occurred_at >= %s AND occurred_at < %s)"
                .formatted(partition, check, from, to));

        ddl("ALTER TABLE events ATTACH PARTITION %s FOR VALUES FROM (%s) TO (%s)"
                .formatted(partition, from, to));

        // Now redundant — the partition bounds enforce the same range.
        ddl("ALTER TABLE %s DROP CONSTRAINT %s".formatted(partition, check));

        log.info("Partition {} indexed and attached to events in {} ms",
                partition, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    /** Direct children of a partitioned parent, including the detach-pending flag. */
    public List<ChildPartition> childrenOf(String parent) {
        return jdbc.sql("""
                        SELECT c.relname, i.inhdetachpending
                        FROM pg_inherits i
                        JOIN pg_class c ON c.oid = i.inhrelid
                        JOIN pg_class p ON p.oid = i.inhparent
                        JOIN pg_namespace n ON n.oid = p.relnamespace
                        WHERE p.relname = :parent
                          AND n.nspname = current_schema()
                        ORDER BY c.relname""")
                .param("parent", parent)
                .query((rs, i) -> new ChildPartition(rs.getString(1), rs.getBoolean(2)))
                .list();
    }

    private long countRows(String table) {
        Long count = jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
        return count == null ? 0 : count;
    }

    private void ddl(String sql) {
        log.info("DDL: {}", sql);
        jdbc.sql(sql).update();
    }
}
