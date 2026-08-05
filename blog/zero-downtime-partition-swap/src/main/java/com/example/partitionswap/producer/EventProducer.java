package com.example.partitionswap.producer;

import com.example.partitionswap.domain.IngestEvent;
import com.example.partitionswap.partition.PartitionSchemaInitializer;
import com.example.partitionswap.repository.IngestEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Continuous write load against the hot ingest table, through Spring Data JPA.
 *
 * <p>The producer knows nothing about partitions: it persists {@link IngestEvent} rows
 * with {@code occurred_at = now()} and PostgreSQL routes each row into the current
 * minute's partition. While the swap job detaches, indexes, and attaches last minute's
 * partition, these inserts continue uninterrupted — that is the zero-downtime claim this
 * demo exists to prove, and the logs let you verify it.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "demo.producer.enabled", havingValue = "true", matchIfMissing = true)
public class EventProducer {

    private static final String[] EVENT_TYPES = {
            "ORDER_CREATED", "PAYMENT_CAPTURED", "SHIPMENT_DISPATCHED", "INVENTORY_ADJUSTED"
    };

    private final IngestEventRepository repository;
    private final int batchSize;
    private final AtomicLong produced = new AtomicLong();

    public EventProducer(IngestEventRepository repository,
                         @Value("${demo.producer.batch-size:25}") int batchSize,
                         // Injected for ordering only: schema must exist before the first batch.
                         PartitionSchemaInitializer schemaInitializer) {
        this.repository = repository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${demo.producer.delay-ms:200}")
    public void produceBatch() {
        List<IngestEvent> batch = new ArrayList<>(batchSize);
        Instant now = Instant.now();
        for (int i = 0; i < batchSize; i++) {
            String type = EVENT_TYPES[ThreadLocalRandom.current().nextInt(EVENT_TYPES.length)];
            batch.add(new IngestEvent(now, type,
                    "{\"orderId\":%d,\"amountCents\":%d}".formatted(
                            ThreadLocalRandom.current().nextLong(1_000_000),
                            ThreadLocalRandom.current().nextInt(100, 50_000))));
        }
        repository.saveAll(batch);

        long total = produced.addAndGet(batchSize);
        if (total % (batchSize * 50L) == 0) {
            log.info("Producer has written {} events into events_ingest", total);
        }
    }
}
