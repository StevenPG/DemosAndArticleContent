package com.example.partitionswap.ingest.copy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Instrumentation for the write path, and the reason there is no longer a log
 * line per batch.
 *
 * <p>Logging every COPY at INFO is wonderful at demo rates and actively
 * harmful at real ones: at a few thousand batches a minute the appender's
 * synchronized write becomes a contention point shared by every listener
 * thread, and the formatting cost is paid on the hot path. The per-batch
 * record is still available at DEBUG for the demo; steady-state observability
 * comes from meters plus one aggregated summary line per reporting window.
 *
 * <p>Meters are the thing you actually want in production anyway — a p99 COPY
 * latency and a rows/sec rate answer "is ingestion healthy?", which no volume
 * of individual log lines does.
 */
@Component
public class IngestMetrics {

    private static final Logger log = LoggerFactory.getLogger(IngestMetrics.class);

    private final Timer copyTimer;
    private final DistributionSummary batchSize;
    private final Counter rowsWritten;
    private final Counter copyFailures;

    /** Deltas for the periodic summary line; LongAdder scales better than an atomic under contention. */
    private final LongAdder rowsSinceReport = new LongAdder();
    private final LongAdder batchesSinceReport = new LongAdder();
    private volatile long lastReportNanos = System.nanoTime();

    /**
     * First and last write of this process's lifetime. A benchmark that polls
     * an HTTP endpoint cannot time a drain that finishes in a few seconds —
     * the polling granularity IS the measurement error. Recording the window
     * in-process makes it exact and adds no load to the thing being measured.
     */
    private volatile long firstWriteNanos;
    private volatile long lastWriteNanos;

    public IngestMetrics(MeterRegistry registry) {
        this.copyTimer = Timer.builder("ingest.copy.duration")
                .description("Wall time of a single COPY, from connection checkout to endCopy")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        this.batchSize = DistributionSummary.builder("ingest.copy.batch.rows")
                .description("Rows per COPY, i.e. per Kafka poll batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.rowsWritten = Counter.builder("ingest.rows.written")
                .description("Total rows COPYed into staging tables")
                .register(registry);
        this.copyFailures = Counter.builder("ingest.copy.failures")
                .description("COPY attempts that threw; the batch is redelivered by Kafka")
                .register(registry);
        registry.gauge("ingest.drain.seconds", this, m -> m.drainSeconds());
    }

    public void recordCopy(long rows, long durationNanos) {
        long now = System.nanoTime();
        if (firstWriteNanos == 0) {
            firstWriteNanos = now;
        }
        lastWriteNanos = now;
        copyTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        batchSize.record(rows);
        rowsWritten.increment(rows);
        rowsSinceReport.add(rows);
        batchesSinceReport.increment();
    }

    public void recordFailure() {
        copyFailures.increment();
    }

    /** Wall seconds between the first and last write; 0 until two writes have happened. */
    public double drainSeconds() {
        long first = firstWriteNanos, last = lastWriteNanos;
        return first == 0 || last <= first ? 0.0 : (last - first) / 1_000_000_000.0;
    }

    /** One aggregated line per window, regardless of ingestion rate. */
    @Scheduled(fixedRateString = "${demo.metrics-report-interval-ms:10000}")
    public void report() {
        long rows = rowsSinceReport.sumThenReset();
        long batches = batchesSinceReport.sumThenReset();
        long now = System.nanoTime();
        long elapsedNanos = now - lastReportNanos;
        lastReportNanos = now;
        if (batches == 0) {
            return;
        }
        double seconds = elapsedNanos / 1_000_000_000.0;
        ValueAtPercentile[] percentiles = copyTimer.takeSnapshot().percentileValues();
        log.info("ingest: {} rows in {} batches ({} rows/s, {} rows/batch) | copy p50 {} ms, p99 {} ms",
                rows, batches,
                Math.round(rows / seconds), rows / batches,
                "%.2f".formatted(percentiles[0].value(TimeUnit.MILLISECONDS)),
                "%.2f".formatted(percentiles[percentiles.length - 1].value(TimeUnit.MILLISECONDS)));
    }
}
