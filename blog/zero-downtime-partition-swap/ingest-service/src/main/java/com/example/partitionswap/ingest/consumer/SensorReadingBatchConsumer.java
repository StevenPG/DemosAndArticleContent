package com.example.partitionswap.ingest.consumer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.copy.CopyBatchWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch listener: spring-kafka hands over everything one {@code poll()}
 * returned (up to {@code max.poll.records}) as a single list, and commits the
 * offsets only after this method returns normally. That makes the unit of
 * work batch-shaped end to end — one poll, one COPY, one offset commit — which
 * is where COPY-based ingestion gets its throughput. At a demo rate of ~1
 * msg/s the batches are small; at thousands per second the same code path
 * simply gets denser batches, with zero code changes.
 */
@Component
public class SensorReadingBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingBatchConsumer.class);

    private final CopyBatchWriter copyBatchWriter;

    public SensorReadingBatchConsumer(CopyBatchWriter copyBatchWriter) {
        this.copyBatchWriter = copyBatchWriter;
    }

    @KafkaListener(topics = "${demo.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onBatch(List<SensorReadingEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        long written = copyBatchWriter.write(events);
        if (written != events.size()) {
            log.warn("batch size {} but COPY reported {} rows", events.size(), written);
        }
    }
}
