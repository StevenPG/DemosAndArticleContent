package com.example.partitionswap.maintenance.swap;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.maintenance.config.MaintenanceProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The alarm for the failure mode this architecture is most exposed to.
 *
 * <p>If the maintenance service stops promoting — a wedged scheduler thread,
 * a permanently failing phase, an attach that loses every lock race — nothing
 * breaks loudly. Ingestion keeps working, staging tables keep being created,
 * the read API keeps returning HTTP 200. The only symptom is that queries
 * quietly stop returning recent data, and the first person to notice is a user
 * asking why the dashboard is stale. Liveness and readiness probes both pass
 * the entire time.
 *
 * <p>This indicator turns that into a signal by measuring the thing that
 * actually matters to a reader: <b>how old is the oldest minute that is still
 * not visible through the parent table?</b> That question is answered from the
 * catalog on every check rather than from in-process bookkeeping, deliberately
 * — if the scheduler thread is dead, cached state is exactly what would keep
 * reporting healthy.
 *
 * <p>A backlog is normal and expected: the current minute is always
 * unattached, and the previous one is unattached until the grace period plus
 * scheduler tick have elapsed. Only a backlog older than that budget means
 * something is wrong.
 */
@Component
public class PartitionSwapHealthIndicator implements HealthIndicator {

    private final PartitionCatalog catalog;
    private final MaintenanceProperties props;
    private final SwapMetrics metrics;
    private final Clock clock;

    public PartitionSwapHealthIndicator(PartitionCatalog catalog,
                                        MaintenanceProperties props,
                                        SwapMetrics metrics,
                                        Clock clock) {
        this.catalog = catalog;
        this.props = props;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public Health health() {
        if (!catalog.parentTableExists()) {
            return Health.outOfService()
                    .withDetail("reason", "parent table does not exist yet; awaiting ingest-service migration")
                    .build();
        }

        Instant now = clock.instant();
        List<PartitionWindow> detached = catalog.detachedStagingTables();
        PartitionWindow oldest = detached.stream()
                .min(Comparator.comparing(PartitionWindow::end))
                .orElse(null);

        Instant lastPromotion = metrics.lastPromotionAt();
        Health.Builder health = Health.up()
                .withDetail("stagingBacklog", detached.size())
                .withDetail("lastPromotionAt",
                        lastPromotion == null ? "never (since this process started)" : lastPromotion.toString());

        if (oldest == null) {
            return health.withDetail("oldestUnattachedAgeSeconds", 0).build();
        }

        long ageSeconds = Duration.between(oldest.end(), now).getSeconds();
        health = health
                .withDetail("oldestUnattachedTable", oldest.tableName())
                .withDetail("oldestUnattachedAgeSeconds", ageSeconds)
                .withDetail("stalenessThresholdSeconds", stalenessThresholdSeconds());

        if (ageSeconds > stalenessThresholdSeconds()) {
            return health.status("DOWN")
                    .withDetail("reason", "partition promotion is falling behind; "
                            + "readers cannot see data older than the threshold")
                    .build();
        }
        return health.build();
    }

    /**
     * The budget a healthy system needs: the minute has to end, the grace
     * period has to pass, the scheduler has to tick, and the promotion itself
     * has to run. Anything beyond that plus generous slack is a real problem
     * rather than normal timing.
     */
    private long stalenessThresholdSeconds() {
        return props.stalenessThresholdSeconds();
    }
}
