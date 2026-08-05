package com.example.partitionswap.maintenance.swap;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.maintenance.config.MaintenanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Promotes a completed staging table to a live partition of
 * {@code sensor_readings}. All the expensive work — primary key build,
 * secondary index builds, the bounds CHECK validation scan, ANALYZE — happens
 * while the table is still detached, where it can't block or slow down a
 * single reader. The only operation that touches the parent is the final
 * {@code ATTACH PARTITION}, and it is engineered down to a metadata-only
 * change:
 *
 * <ul>
 *   <li>the pre-built indexes structurally match the parent's partitioned
 *       indexes, so ATTACH links them instead of building them under lock;</li>
 *   <li>the pre-validated CHECK constraint proves every row is inside the
 *       partition bounds, so ATTACH skips its validation scan;</li>
 *   <li>since Postgres 12, ATTACH takes only SHARE UPDATE EXCLUSIVE on the
 *       parent — concurrent SELECTs are never blocked at all;</li>
 *   <li>{@code lock_timeout} plus a retry loop keeps the attach from parking
 *       in the lock queue behind a long transaction and stalling everyone
 *       who queues up after it.</li>
 * </ul>
 *
 * <p>The whole promotion runs on ONE dedicated connection, deliberately below
 * the JPA/JdbcClient abstractions: {@code SET lock_timeout} and advisory locks
 * are connection-scoped, so a pool that hands each statement a different
 * connection would silently break both. Every phase is also idempotent
 * (existence-guarded), so a crash between phases is healed by the next
 * scheduler tick re-running the same promotion.
 */
@Service
public class PartitionSwapService {

    private static final Logger log = LoggerFactory.getLogger(PartitionSwapService.class);

    /** Namespace for pg_advisory locks so we can't collide with other tooling. */
    private static final int ADVISORY_LOCK_CLASS = 42_001;

    private final DataSource dataSource;
    private final PartitionCatalog catalog;
    private final MaintenanceProperties props;
    private final Clock clock;

    public PartitionSwapService(DataSource dataSource,
                                PartitionCatalog catalog,
                                MaintenanceProperties props,
                                Clock clock) {
        this.dataSource = dataSource;
        this.catalog = catalog;
        this.props = props;
        this.clock = clock;
    }

    /**
     * One scheduler tick: find every detached staging table whose minute is
     * over (plus grace) and promote each in chronological order. Normally
     * that's exactly one table; after downtime of this service it drains the
     * whole backlog in one pass.
     */
    public int promoteEligibleStagingTables() {
        if (!catalog.parentTableExists()) {
            log.warn("parent table {} does not exist yet (ingest-service migration pending?); skipping tick",
                    Partitions.PARENT_TABLE);
            return 0;
        }
        Instant completeBefore = clock.instant().minusSeconds(props.graceSeconds());
        List<PartitionWindow> eligible = catalog.detachedStagingTables().stream()
                .filter(w -> !w.end().isAfter(completeBefore))
                .toList();
        int promoted = 0;
        for (PartitionWindow window : eligible) {
            if (promote(window)) {
                promoted++;
            }
        }
        return promoted;
    }

    private boolean promote(PartitionWindow window) {
        String table = window.tableName();
        long totalStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAdvisoryLock(connection, table)) {
                log.info("[{}] another maintenance instance holds the lock; skipping", table);
                return false;
            }
            try {
                // Phase 1 — constraints & indexes, on the DETACHED table.
                // Must structurally match the parent's primary key and
                // partitioned indexes so the attach links rather than builds.
                long pkMs = timed(() -> addPrimaryKeyIfAbsent(connection, table));
                long idxMs = timed(() -> execute(connection, """
                        CREATE INDEX IF NOT EXISTS %s_ingested_at_idx ON %s (ingested_at)
                        """.formatted(table, table)));
                idxMs += timed(() -> execute(connection, """
                        CREATE INDEX IF NOT EXISTS %s_device_metric_idx ON %s (device_id, metric, ingested_at)
                        """.formatted(table, table)));

                // Phase 2 — prove the bounds while still detached. This CHECK
                // is what lets ATTACH PARTITION skip its validation scan.
                long checkMs = timed(() -> addBoundsCheckIfAbsent(connection, window));

                // Phase 3 — fresh statistics so the planner is right about the
                // partition from its very first second of visibility.
                long analyzeMs = timed(() -> execute(connection, "ANALYZE " + table));

                // Phase 4 — the swap. Metadata-only, but still needs a lock on
                // the parent, so never let it queue indefinitely.
                long attachMs = timed(() -> attachWithRetry(connection, window));

                // The bounds CHECK is now redundant (the partition bounds
                // enforce the same predicate); dropping it keeps the catalog
                // clean. Instant, but it does lock the partition, so it stays
                // under the same lock_timeout discipline as the attach.
                long dropMs = timed(() -> execute(connection, """
                        ALTER TABLE %s DROP CONSTRAINT IF EXISTS %s_bounds
                        """.formatted(table, table)));
                execute(connection, "RESET lock_timeout");

                long rows = approxRows(connection, table);
                long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
                log.info("[{}] promoted to live partition: ~{} rows | pk {} ms, indexes {} ms, "
                                + "bounds-check {} ms, analyze {} ms, attach {} ms, drop-check {} ms, total {} ms",
                        table, rows, pkMs, idxMs, checkMs, analyzeMs, attachMs, dropMs, totalMs);
                return true;
            } finally {
                releaseAdvisoryLock(connection, table);
            }
        } catch (SQLException e) {
            // Leave the staging table as-is; every phase is idempotent, so the
            // next tick resumes exactly where this one failed.
            log.error("[{}] promotion failed; will retry next tick", table, e);
            return false;
        }
    }

    // --- phases ---------------------------------------------------------

    private void addPrimaryKeyIfAbsent(Connection connection, String table) throws SQLException {
        boolean hasPk;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = ?::regclass AND contype = 'p')")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                hasPk = rs.getBoolean(1);
            }
        }
        if (!hasPk) {
            execute(connection, """
                    ALTER TABLE %s ADD CONSTRAINT %s_pkey PRIMARY KEY (id, ingested_at)
                    """.formatted(table, table));
        }
    }

    private void addBoundsCheckIfAbsent(Connection connection, PartitionWindow window) throws SQLException {
        String table = window.tableName();
        boolean hasCheck;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = ?::regclass AND conname = ?)")) {
            ps.setString(1, table);
            ps.setString(2, table + "_bounds");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                hasCheck = rs.getBoolean(1);
            }
        }
        if (!hasCheck) {
            // Scans every row of the staging table — the whole point is to pay
            // that cost HERE, while nobody can see the table, instead of
            // inside ATTACH while holding a lock on the parent.
            execute(connection, """
                    ALTER TABLE %s ADD CONSTRAINT %s_bounds
                        CHECK (ingested_at >= '%s' AND ingested_at < '%s')
                    """.formatted(table, table, window.startLiteral(), window.endLiteral()));
        }
    }

    private void attachWithRetry(Connection connection, PartitionWindow window) throws SQLException {
        execute(connection, "SET lock_timeout = '%d ms'".formatted(props.attachLockTimeoutMs()));
        String attachSql = """
                ALTER TABLE %s ATTACH PARTITION %s
                    FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(Partitions.PARENT_TABLE, window.tableName(),
                window.startLiteral(), window.endLiteral());
        SQLException lastLockTimeout = null;
        for (int attempt = 1; attempt <= props.attachAttempts(); attempt++) {
            try {
                execute(connection, attachSql);
                if (attempt > 1) {
                    log.info("[{}] attach succeeded on attempt {}", window.tableName(), attempt);
                }
                return;
            } catch (SQLException e) {
                if (!"55P03".equals(e.getSQLState())) { // lock_not_available
                    throw e;
                }
                lastLockTimeout = e;
                log.warn("[{}] attach hit lock_timeout ({} ms) on attempt {}/{}; backing off",
                        window.tableName(), props.attachLockTimeoutMs(), attempt, props.attachAttempts());
                sleepQuietly(200L * attempt);
            }
        }
        throw lastLockTimeout;
    }

    // --- plumbing -------------------------------------------------------

    private boolean tryAdvisoryLock(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_try_advisory_lock(?, hashtext(?))")) {
            ps.setInt(1, ADVISORY_LOCK_CLASS);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection, String table) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?, hashtext(?))")) {
            ps.setInt(1, ADVISORY_LOCK_CLASS);
            ps.setString(2, table);
            ps.executeQuery().close();
        } catch (SQLException e) {
            // The lock dies with the connection anyway; closing the connection
            // is the real release. Log and move on.
            log.warn("[{}] failed to release advisory lock explicitly", table, e);
        }
    }

    private long approxRows(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reltuples::bigint FROM pg_class WHERE oid = ?::regclass")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        log.debug("executing: {}", sql.strip());
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long timed(SqlRunnable phase) throws SQLException {
        long start = System.nanoTime();
        phase.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
