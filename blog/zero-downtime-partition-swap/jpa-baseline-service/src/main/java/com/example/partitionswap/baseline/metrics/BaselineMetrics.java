package com.example.partitionswap.baseline.metrics;

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
 * Deliberately mirrors the ingest service's {@code IngestMetrics}: same meter
 * shapes, same aggregated log line, same reporting window. A benchmark whose
 * two sides are measured differently is not a benchmark.
 */
@Component
public class BaselineMetrics {

    private static final Logger log = LoggerFactory.getLogger(BaselineMetrics.class);

    private final Timer saveTimer;
    private final DistributionSummary batchSize;
    private final Counter rowsWritten;

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

    public BaselineMetrics(MeterRegistry registry) {
        this.saveTimer = Timer.builder("baseline.save.duration")
                .description("Wall time of one saveAll + flush")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        this.batchSize = DistributionSummary.builder("baseline.save.batch.rows")
                .description("Rows per saveAll, i.e. per Kafka poll batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.rowsWritten = Counter.builder("baseline.rows.written")
                .description("Total rows written through Spring Data JPA")
                .register(registry);
        registry.gauge("baseline.drain.seconds", this, m -> m.drainSeconds());
    }

    public void recordSave(long rows, long durationNanos) {
        long now = System.nanoTime();
        if (firstWriteNanos == 0) {
            firstWriteNanos = now;
        }
        lastWriteNanos = now;
        saveTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        batchSize.record(rows);
        rowsWritten.increment(rows);
        rowsSinceReport.add(rows);
        batchesSinceReport.increment();
    }

    /** Wall seconds between the first and last write; 0 until two writes have happened. */
    public double drainSeconds() {
        long first = firstWriteNanos, last = lastWriteNanos;
        return first == 0 || last <= first ? 0.0 : (last - first) / 1_000_000_000.0;
    }

    @Scheduled(fixedRateString = "${baseline.metrics-report-interval-ms:10000}")
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
        ValueAtPercentile[] percentiles = saveTimer.takeSnapshot().percentileValues();
        log.info("baseline: {} rows in {} batches ({} rows/s, {} rows/batch) | saveAll p50 {} ms, p99 {} ms",
                rows, batches,
                Math.round(rows / seconds), rows / batches,
                "%.2f".formatted(percentiles[0].value(TimeUnit.MILLISECONDS)),
                "%.2f".formatted(percentiles[percentiles.length - 1].value(TimeUnit.MILLISECONDS)));
    }
}
