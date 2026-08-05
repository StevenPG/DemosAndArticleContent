package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the per-minute staging table on first use of each minute. Staging
 * tables are cloned from the parent's column definitions but deliberately
 * carry NO indexes and NO constraints beyond NOT NULL: an index-free heap is
 * the cheapest possible COPY target, and every row is index-invisible work the
 * writer no longer pays for. Indexes and the primary key are built later, by
 * the maintenance service, while the table is still detached and invisible to
 * readers.
 *
 * <p>Every listener thread races here at each minute boundary, so the DDL is
 * memoized through {@code computeIfAbsent}: exactly one thread issues the
 * CREATE and the rest block briefly on the same key and then find it cached.
 * Letting all N threads fire {@code CREATE TABLE IF NOT EXISTS} concurrently
 * would mostly work, but Postgres has a genuine race there — IF NOT EXISTS
 * checks the catalog before taking the lock, so simultaneous creators can
 * collide with a duplicate-key error on pg_class rather than no-opping.
 */
@Component
public class StagingTableManager {

    private static final Logger log = LoggerFactory.getLogger(StagingTableManager.class);

    private final JdbcClient jdbcClient;
    private final Map<String, Boolean> knownTables = new ConcurrentHashMap<>();

    public StagingTableManager(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Idempotent and cached: one DDL round-trip per minute, not one per batch or per thread. */
    public void ensureExists(PartitionWindow window) {
        knownTables.computeIfAbsent(window.tableName(), tableName -> {
            // LIKE copies column definitions and NOT NULLs (which ATTACH PARTITION
            // requires) but not indexes or the primary key — exactly what we want.
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS %s
                        (LIKE %s INCLUDING DEFAULTS INCLUDING STORAGE)
                    """.formatted(tableName, Partitions.PARENT_TABLE)).update();
            log.info("staging table ready: {} [{} .. {})",
                    tableName, window.startLiteral(), window.endLiteral());
            return Boolean.TRUE;
        });
        // Bounded memory: a table whose window closed before the current one
        // started can never be written again.
        knownTables.keySet().removeIf(name -> Partitions.parseTableName(name)
                .map(w -> w.end().isBefore(window.start()))
                .orElse(true));
    }
}
