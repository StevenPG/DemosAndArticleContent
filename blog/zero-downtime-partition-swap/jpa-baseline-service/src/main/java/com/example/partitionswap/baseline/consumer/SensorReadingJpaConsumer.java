package com.example.partitionswap.baseline.consumer;

import com.example.partitionswap.baseline.jpa.SensorReadingRow;
import com.example.partitionswap.baseline.jpa.SensorReadingRowRepository;
import com.example.partitionswap.baseline.metrics.BaselineMetrics;
import com.example.partitionswap.common.SensorReadingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The baseline write path: take the Kafka batch, map it to entities, call
 * {@code saveAll}. That is the whole implementation, and that is deliberately
 * all it is — this module exists to measure what a team gets by writing the
 * obvious thing.
 *
 * <p>Everything that differs between the {@code naive} and {@code tuned}
 * profiles is configuration, not code: JDBC batching, insert ordering, listener
 * concurrency, durability, and the persist-versus-merge decision described in
 * {@link SensorReadingRow}. The Java below is identical in both.
 */
@Component
public class SensorReadingJpaConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingJpaConsumer.class);

    private final SensorReadingRowRepository repository;
    private final BaselineMetrics metrics;
    private final boolean treatAsNew;

    public SensorReadingJpaConsumer(SensorReadingRowRepository repository,
                                    BaselineMetrics metrics,
                                    @Value("${baseline.treat-as-new:false}") boolean treatAsNew) {
        this.repository = repository;
        this.metrics = metrics;
        this.treatAsNew = treatAsNew;
    }

    /**
     * One transaction per batch, so the comparison against a single atomic
     * COPY is like-for-like at the transaction level. Without
     * {@code @Transactional} each {@code saveAll} would still be one
     * transaction (Spring Data opens one), so this mainly makes the boundary
     * explicit.
     */
    @KafkaListener(topics = "${baseline.topic}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onBatch(List<SensorReadingEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        Instant ingestedAt = Instant.now();
        List<SensorReadingRow> rows = new ArrayList<>(events.size());
        for (SensorReadingEvent event : events) {
            rows.add(new SensorReadingRow(event, ingestedAt, treatAsNew));
        }

        long start = System.nanoTime();
        repository.saveAll(rows);
        // saveAll returns before the flush when the transaction is still open,
        // so force it here; otherwise the timing would measure the mapping and
        // attribute the actual SQL to whatever runs next.
        repository.flush();
        long durationNanos = System.nanoTime() - start;

        metrics.recordSave(rows.size(), durationNanos);
        log.debug("saveAll {} rows in {} µs", rows.size(), durationNanos / 1_000);
    }
}
