package com.example.partitionswap.consumer;

import com.example.partitionswap.domain.ReadEvent;
import com.example.partitionswap.partition.PartitionSchemaInitializer;
import com.example.partitionswap.repository.EventTypeCount;
import com.example.partitionswap.repository.ReadEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Read-side consumer: queries the indexed {@code events} table through Spring Data JPA
 * while swaps are happening underneath it.
 *
 * <p>Every few runs it also logs {@code EXPLAIN} output for the window query, showing
 * two things worth seeing in a real system: partition pruning (only the minute
 * partitions inside the window appear in the plan) and the per-partition
 * {@code (event_type, occurred_at)} indexes that the swap job built offline.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "demo.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class EventConsumer {

    private final ReadEventRepository repository;
    private final JdbcClient jdbc;
    private final Duration window;
    private final AtomicLong runs = new AtomicLong();

    public EventConsumer(ReadEventRepository repository,
                         JdbcClient jdbc,
                         @Value("${demo.consumer.window-minutes:5}") long windowMinutes,
                         // Injected for ordering only: schema must exist before the first query.
                         PartitionSchemaInitializer schemaInitializer) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Scheduled(fixedDelayString = "${demo.consumer.delay-ms:10000}", initialDelay = 10000)
    public void consume() {
        Instant from = Instant.now().minus(window);

        long inWindow = repository.countByOccurredAtGreaterThanEqual(from);
        List<EventTypeCount> byType = repository.countByTypeSince(from);
        List<ReadEvent> latest = repository.findTop3ByOrderByOccurredAtDesc();

        log.info("Consumer: {} indexed events in the last {} — by type: {}",
                inWindow, window,
                byType.stream()
                        .map(c -> c.getEventType() + "=" + c.getCount())
                        .collect(Collectors.joining(", ")));
        latest.stream().findFirst().ifPresent(e ->
                log.info("Consumer: newest indexed event id={} type={} occurredAt={}",
                        e.getId(), e.getEventType(), e.getOccurredAt()));

        if (runs.incrementAndGet() % 6 == 0) {
            logExplain(from);
        }
    }

    /**
     * Logs the planner's view of the window query. Explicit SQL on purpose: EXPLAIN
     * cannot take bind parameters for the pruning decision we want to demonstrate, so
     * the timestamp is inlined as a literal.
     */
    private void logExplain(Instant from) {
        String sql = ("EXPLAIN (COSTS OFF) SELECT event_type, count(*) FROM events "
                + "WHERE occurred_at >= '%s' GROUP BY event_type").formatted(from);
        String plan = jdbc.sql(sql)
                .query(String.class)
                .list()
                .stream()
                .collect(Collectors.joining(System.lineSeparator()));
        log.info("Planner output for the window query (note the pruned partition list):{}{}",
                System.lineSeparator(), plan);
    }
}
