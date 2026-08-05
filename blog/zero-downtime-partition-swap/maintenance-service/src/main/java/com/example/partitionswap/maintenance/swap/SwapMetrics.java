package com.example.partitionswap.maintenance.swap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Instrumentation for the swap path.
 *
 * <p>The ingest side got meters because a log line per batch is a contention
 * point. The swap runs once a minute, so that argument doesn't apply — but the
 * more important one does: <em>a log line is not a signal</em>. "How long is
 * the attach holding a lock on the parent?" and "is the backlog of unattached
 * partitions growing?" are questions a p99 answers and a scrollback does not.
 *
 * <p>Per-phase timers matter specifically because this design's whole claim is
 * that expensive work happens off the live table. {@code partition.swap.phase}
 * tagged {@code attach} versus the same metric tagged {@code indexes} is that
 * claim, made continuously checkable in production rather than asserted once
 * in a blog post.
 *
 * <p>The gauges are fed from the scheduler tick rather than by querying the
 * catalog on scrape, so a busy Prometheus can never turn observability into
 * database load.
 */
@Component
public class SwapMetrics {

    private final MeterRegistry registry;
    private final Map<String, Timer> phaseTimers = new ConcurrentHashMap<>();

    private final Timer promotionTimer;
    private final DistributionSummary promotedRows;
    private final Counter promotions;
    private final Counter failures;
    private final Counter lockTimeouts;
    private final Counter contendedSkips;
    private final Counter partitionsDropped;

    /** Detached staging tables seen on the most recent tick. */
    private final AtomicInteger stagingBacklog = new AtomicInteger();
    /** Age in seconds of the oldest detached staging table's window end; 0 when none. */
    private final AtomicLong oldestStagingAgeSeconds = new AtomicLong();
    /** Epoch millis of the last successful promotion; 0 until one happens. */
    private final AtomicLong lastPromotionEpochMillis = new AtomicLong();

    public SwapMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.promotionTimer = Timer.builder("partition.swap.duration")
                .description("End-to-end promotion of one staging table to a live partition")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.promotedRows = DistributionSummary.builder("partition.swap.rows")
                .description("Approximate rows in each promoted partition")
                .register(registry);
        this.promotions = Counter.builder("partition.swap.promotions")
                .description("Staging tables successfully attached as partitions")
                .register(registry);
        this.failures = Counter.builder("partition.swap.failures")
                .description("Promotions that threw; retried on the next tick")
                .register(registry);
        this.lockTimeouts = Counter.builder("partition.swap.attach.lock.timeouts")
                .description("ATTACH attempts that hit lock_timeout waiting on the parent")
                .register(registry);
        this.contendedSkips = Counter.builder("partition.swap.advisory.lock.skips")
                .description("Promotions skipped because another instance held the advisory lock")
                .register(registry);
        this.partitionsDropped = Counter.builder("partition.retention.dropped")
                .description("Expired partitions detached concurrently and dropped")
                .register(registry);

        registry.gauge("partition.swap.staging.backlog", stagingBacklog);
        registry.gauge("partition.swap.staging.oldest.age.seconds", oldestStagingAgeSeconds);
    }

    /**
     * Per-phase timings, tagged rather than expressed as separate meters so
     * they can be compared on one graph — which is the point.
     */
    public void recordPhase(String phase, long durationNanos) {
        phaseTimers.computeIfAbsent(phase, name -> Timer.builder("partition.swap.phase")
                        .description("Duration of one phase of a partition promotion")
                        .tag("phase", name)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry))
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordPromotion(long approxRows, long durationNanos, Instant completedAt) {
        promotionTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        if (approxRows >= 0) {
            promotedRows.record(approxRows);
        }
        promotions.increment();
        lastPromotionEpochMillis.set(completedAt.toEpochMilli());
    }

    public void recordFailure() {
        failures.increment();
    }

    public void recordLockTimeout() {
        lockTimeouts.increment();
    }

    public void recordAdvisoryLockSkip() {
        contendedSkips.increment();
    }

    public void recordPartitionDropped() {
        partitionsDropped.increment();
    }

    /** Called once per scheduler tick with the state that tick observed. */
    public void recordBacklog(int detachedTables, long oldestAgeSeconds) {
        stagingBacklog.set(detachedTables);
        oldestStagingAgeSeconds.set(oldestAgeSeconds);
    }

    public int stagingBacklog() {
        return stagingBacklog.get();
    }

    public long oldestStagingAgeSeconds() {
        return oldestStagingAgeSeconds.get();
    }

    /** Empty until the first successful promotion of this process's lifetime. */
    public Instant lastPromotionAt() {
        long millis = lastPromotionEpochMillis.get();
        return millis == 0 ? null : Instant.ofEpochMilli(millis);
    }
}
