package com.example.partitionswap.ingest.producer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.config.DemoProperties;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo load generator, with two distinct modes.
 *
 * <p><b>Rate mode</b> (the default) emits
 * {@code demo.producer.messages-per-second} readings per second from a single
 * scheduled thread. This is for watching the demo, where a steady trickle is
 * the point and one thread is plenty.
 *
 * <p><b>Preload mode</b> (when {@code demo.producer.total-messages > 0}) runs
 * {@code demo.producer.threads} threads flat out until the total is reached,
 * then stops. This exists because the benchmark needs a fixed message set on
 * the topic before any consumer starts, and the scheduled path cannot deliver
 * it at scale: a {@code fixedRate = 1000} tick that takes longer than a second
 * to generate and flush simply falls behind, which caps preload throughput
 * somewhere around 20–30k msg/s regardless of hardware. Preloading tens of
 * millions of messages is not a job for one thread.
 *
 * <p>Events are keyed by device id so per-device ordering is preserved across
 * the topic's partitions in both modes.
 */
@Component
@ConditionalOnProperty(prefix = "demo.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensorReadingProducer {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingProducer.class);
    private static final String[] METRICS = {"temperature_c", "humidity_pct", "vibration_hz"};

    /** Rows claimed per thread per round in preload mode; amortises the atomic. */
    private static final int CLAIM_CHUNK = 2_000;

    /**
     * UUIDv7: 48 bits of Unix millisecond timestamp followed by randomness, so
     * generated ids sort in creation order. That matters here even though the
     * primary key is bulk-built rather than incrementally maintained — the
     * build sorts either way, but v7 keys arrive nearly sorted, and the
     * resulting index is physically correlated with the heap, which keeps
     * range scans and index-only scans on the hot recent data sequential.
     * Under the incremental-insert pattern this design avoids, the difference
     * is far more dramatic: v4 keys scatter writes across every leaf page of
     * the index, destroying cache locality and inflating WAL through
     * full-page writes.
     *
     * <p>Thread-safe, and the JDK has no built-in v7 generator as of Java 25.
     */
    private static final TimeBasedEpochGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

    private final KafkaTemplate<String, SensorReadingEvent> kafkaTemplate;
    private final DemoProperties props;
    private final AtomicLong sent = new AtomicLong();

    public SensorReadingProducer(KafkaTemplate<String, SensorReadingEvent> kafkaTemplate, DemoProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    // --- rate mode ------------------------------------------------------

    @Scheduled(fixedRate = 1000)
    public void emit() {
        if (props.producer().totalMessages() > 0) {
            return; // preload mode owns production
        }
        int perSecond = props.producer().messagesPerSecond();
        for (int i = 0; i < perSecond; i++) {
            SensorReadingEvent event = nextEvent();
            kafkaTemplate.send(props.topic(), event.deviceId(), event);
        }
        kafkaTemplate.flush();
        long total = sent.addAndGet(perSecond);
        if (total % 30 < perSecond) {
            log.info("produced {} events so far ({} /s)", total, perSecond);
        }
    }

    // --- preload mode ---------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void preloadIfRequested() throws InterruptedException {
        long cap = props.producer().totalMessages();
        if (cap <= 0) {
            return;
        }
        int threads = Math.max(1, props.producer().threads());
        log.info("preloading {} events to topic {} on {} threads", cap, props.topic(), threads);
        long start = System.nanoTime();

        CountDownLatch done = new CountDownLatch(threads);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    try {
                        long claimed;
                        // Claim a block at a time rather than one row at a
                        // time: the atomic is the only shared state, and this
                        // keeps it off the hot path.
                        while ((claimed = sent.getAndAdd(CLAIM_CHUNK)) < cap) {
                            long upTo = Math.min(CLAIM_CHUNK, cap - claimed);
                            for (long i = 0; i < upTo; i++) {
                                SensorReadingEvent event = nextEvent();
                                // Keyed by device so per-device ordering
                                // survives partitioning, exactly as in rate mode.
                                kafkaTemplate.send(props.topic(), event.deviceId(), event);
                            }
                        }
                        // One flush per thread at the end; flushing per record
                        // would serialise the producer's own batching.
                        kafkaTemplate.flush();
                    } catch (RuntimeException e) {
                        log.error("preload thread failed", e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        log.info("preload complete: {} events emitted in {} ms ({} events/s)",
                cap, Math.round(seconds * 1000), Math.round(cap / seconds));
    }

    private SensorReadingEvent nextEvent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new SensorReadingEvent(
                UUID_V7.generate(),
                "device-%03d".formatted(random.nextInt(props.producer().deviceCount())),
                METRICS[random.nextInt(METRICS.length)],
                Math.round(random.nextGaussian(50, 15) * 100.0) / 100.0,
                Instant.now());
    }
}
