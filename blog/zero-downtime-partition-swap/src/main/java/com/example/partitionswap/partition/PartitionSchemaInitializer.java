package com.example.partitionswap.partition;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates the partitioned parents and the initial minute partitions at startup.
 *
 * <p>All DDL is explicit SQL executed through {@link JdbcClient} — Hibernate's schema
 * generation cannot express partitioned tables, so it is disabled ({@code ddl-auto: none})
 * and this class owns the schema.
 *
 * <p>Two parents with identical row shape:
 * <ul>
 *   <li>{@code events_ingest} — the write side. Only the primary key index exists here,
 *       so the producer pays the minimum possible indexing tax per insert.</li>
 *   <li>{@code events} — the read side. It declares the read-optimized partitioned
 *       indexes; minute partitions are attached to it after the swap job has built the
 *       matching child indexes offline.</li>
 * </ul>
 *
 * <p>The producer and both schedulers inject this component, which makes Spring
 * initialize the schema before any of them can touch the database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionSchemaInitializer {

    private final JdbcClient jdbc;
    private final PartitionSwapService swapService;

    @PostConstruct
    public void initialize() {
        log.info("Initializing partitioned schema");

        ddl("""
                CREATE TABLE IF NOT EXISTS events_ingest (
                    id          BIGINT GENERATED ALWAYS AS IDENTITY,
                    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    event_type  TEXT NOT NULL,
                    payload     TEXT NOT NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)""");

        ddl("""
                CREATE TABLE IF NOT EXISTS events (
                    id          BIGINT NOT NULL,
                    occurred_at TIMESTAMPTZ NOT NULL,
                    event_type  TEXT NOT NULL,
                    payload     TEXT NOT NULL,
                    PRIMARY KEY (id, occurred_at)
                ) PARTITION BY RANGE (occurred_at)""");

        // Partitioned indexes on the read parent. ATTACH PARTITION must find a matching
        // index on every incoming child — the swap job builds them while the child is a
        // standalone table, so the attach itself is a metadata-only operation.
        ddl("CREATE INDEX IF NOT EXISTS events_type_time_idx ON events (event_type, occurred_at)");
        ddl("CREATE INDEX IF NOT EXISTS events_time_idx ON events (occurred_at)");

        // Cover the recent past (the swap job may still owe us last minute's partition
        // after a restart) and a few minutes ahead so inserts never race partition creation.
        swapService.ensureIngestPartitions(Instant.now());

        log.info("Partitioned schema ready");
    }

    private void ddl(String sql) {
        log.info("DDL: {}", sql.stripIndent());
        jdbc.sql(sql).update();
    }
}
