package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the per-minute staging table on first use of each minute. Staging
 * tables are cloned from the parent's column definitions but deliberately
 * carry NO indexes and NO constraints beyond NOT NULL: an index-free heap is
 * the cheapest possible COPY target, and every row is index-invisible work the
 * writer no longer pays for. Indexes and the primary key are built later, by
 * the maintenance service, while the table is still detached and invisible to
 * readers.
 */
@Component
public class StagingTableManager {

    private static final Logger log = LoggerFactory.getLogger(StagingTableManager.class);

    private final JdbcClient jdbcClient;
    private final Set<String> knownTables = ConcurrentHashMap.newKeySet();

    public StagingTableManager(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Idempotent and cached: the DDL round-trip happens once per minute, not once per batch. */
    public void ensureExists(PartitionWindow window) {
        if (knownTables.contains(window.tableName())) {
            return;
        }
        // LIKE copies column definitions and NOT NULLs (which ATTACH PARTITION
        // requires) but not indexes or the primary key — exactly what we want.
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s
                    (LIKE %s INCLUDING DEFAULTS INCLUDING STORAGE)
                """.formatted(window.tableName(), Partitions.PARENT_TABLE);
        jdbcClient.sql(ddl).update();
        knownTables.add(window.tableName());
        // Bounded memory: a table two minutes old can never be written again.
        knownTables.removeIf(name -> Partitions.parseTableName(name)
                .map(w -> w.end().isBefore(window.start()))
                .orElse(true));
        log.info("staging table ready: {} [{} .. {})", window.tableName(), window.startLiteral(), window.endLiteral());
    }
}
