package com.example.partitionswap.maintenance.retention;

import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.maintenance.config.MaintenanceProperties;
import com.example.partitionswap.maintenance.swap.PartitionCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The mirror image of the swap: partitioning makes deletion free. Instead of
 * a DELETE that churns indexes and bloats the heap, old data leaves as whole
 * partitions — DETACH PARTITION CONCURRENTLY (which never blocks readers,
 * waiting out concurrent queries instead of cancelling them) followed by a
 * DROP of a table nobody can see anymore.
 */
@Component
@ConditionalOnProperty(prefix = "maintenance.retention", name = "enabled", havingValue = "true")
public class PartitionRetentionService {

    private static final Logger log = LoggerFactory.getLogger(PartitionRetentionService.class);

    private final JdbcClient jdbcClient;
    private final PartitionCatalog catalog;
    private final MaintenanceProperties props;
    private final Clock clock;

    public PartitionRetentionService(JdbcClient jdbcClient,
                                     PartitionCatalog catalog,
                                     MaintenanceProperties props,
                                     Clock clock) {
        this.jdbcClient = jdbcClient;
        this.catalog = catalog;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "${maintenance.retention-cron:40 * * * * *}")
    public void dropExpiredPartitions() {
        if (!catalog.parentTableExists()) {
            return;
        }
        Instant cutoff = clock.instant().minusSeconds(props.retention().maxAgeMinutes() * 60L);
        catalog.attachedPartitions().stream()
                .map(p -> Partitions.parseTableName(p.tableName()))
                .flatMap(java.util.Optional::stream)
                .filter(w -> w.end().isBefore(cutoff))
                .forEach(window -> {
                    // DETACH CONCURRENTLY refuses to run inside a transaction
                    // block; JdbcClient statements here run in autocommit, so
                    // each executes as its own top-level command.
                    jdbcClient.sql("ALTER TABLE %s DETACH PARTITION %s CONCURRENTLY"
                            .formatted(Partitions.PARENT_TABLE, window.tableName())).update();
                    jdbcClient.sql("DROP TABLE " + window.tableName()).update();
                    log.info("[{}] expired: detached concurrently and dropped", window.tableName());
                });
    }
}
