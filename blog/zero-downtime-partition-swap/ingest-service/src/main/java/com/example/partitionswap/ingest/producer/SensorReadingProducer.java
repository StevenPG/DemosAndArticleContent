package com.example.partitionswap.ingest.producer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.config.DemoProperties;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo load generator: emits {@code demo.producer.messages-per-second} readings
 * per second, keyed by device id so per-device ordering is preserved across the
 * topic's partitions. In a real deployment this is whatever fleet of services
 * feeds the topic; it lives in the ingest service here purely so the demo is
 * two processes instead of three.
 */
@Component
@ConditionalOnProperty(prefix = "demo.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensorReadingProducer {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingProducer.class);
    private static final String[] METRICS = {"temperature_c", "humidity_pct", "vibration_hz"};

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
    private final java.util.concurrent.atomic.AtomicBoolean finished =
            new java.util.concurrent.atomic.AtomicBoolean();

    public SensorReadingProducer(KafkaTemplate<String, SensorReadingEvent> kafkaTemplate, DemoProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Scheduled(fixedRate = 1000)
    public void emit() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long cap = props.producer().totalMessages();
        int perSecond = props.producer().messagesPerSecond();
        if (cap > 0) {
            long remaining = cap - sent.get();
            if (remaining <= 0) {
                if (finished.compareAndSet(false, true)) {
                    log.info("preload complete: {} events emitted", sent.get());
                }
                return;
            }
            perSecond = (int) Math.min(perSecond, remaining);
        }
        for (int i = 0; i < perSecond; i++) {
            String deviceId = "device-%03d".formatted(random.nextInt(props.producer().deviceCount()));
            String metric = METRICS[random.nextInt(METRICS.length)];
            SensorReadingEvent event = new SensorReadingEvent(
                    UUID_V7.generate(),
                    deviceId,
                    metric,
                    Math.round(random.nextGaussian(50, 15) * 100.0) / 100.0,
                    Instant.now());
            kafkaTemplate.send(props.topic(), event.deviceId(), event);
        }
        kafkaTemplate.flush();
        long total = sent.addAndGet(perSecond);
        if (total % 30 < perSecond) {
            log.info("produced {} events so far ({} /s)", total, perSecond);
        }
    }
}
