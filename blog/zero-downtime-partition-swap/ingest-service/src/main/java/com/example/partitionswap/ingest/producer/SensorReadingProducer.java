package com.example.partitionswap.ingest.producer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
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

    private final KafkaTemplate<String, SensorReadingEvent> kafkaTemplate;
    private final DemoProperties props;
    private final AtomicLong sent = new AtomicLong();

    public SensorReadingProducer(KafkaTemplate<String, SensorReadingEvent> kafkaTemplate, DemoProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Scheduled(fixedRate = 1000)
    public void emit() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int perSecond = props.producer().messagesPerSecond();
        for (int i = 0; i < perSecond; i++) {
            String deviceId = "device-%03d".formatted(random.nextInt(props.producer().deviceCount()));
            String metric = METRICS[random.nextInt(METRICS.length)];
            SensorReadingEvent event = new SensorReadingEvent(
                    UUID.randomUUID(),
                    deviceId,
                    metric,
                    Math.round(random.nextGaussian(50, 15) * 100.0) / 100.0,
                    Instant.now());
            kafkaTemplate.send(props.topic(), event.deviceId(), event);
        }
        long total = sent.addAndGet(perSecond);
        if (total % 30 < perSecond) {
            log.info("produced {} events so far ({} /s)", total, perSecond);
        }
    }
}
