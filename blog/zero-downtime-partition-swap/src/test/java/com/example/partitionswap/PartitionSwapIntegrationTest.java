package com.example.partitionswap;

import com.example.partitionswap.domain.IngestEvent;
import com.example.partitionswap.partition.PartitionNaming;
import com.example.partitionswap.partition.PartitionSwapService;
import com.example.partitionswap.partition.PartitionSwapService.ChildPartition;
import com.example.partitionswap.repository.EventTypeCount;
import com.example.partitionswap.repository.IngestEventRepository;
import com.example.partitionswap.repository.ReadEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the swap against a real PostgreSQL:
 * rows written through JPA into last minute's ingest partition are detached, indexed,
 * attached to {@code events}, and become visible — with their indexes — to the
 * read-side repository. The demo's own schedulers are disabled so the test drives the
 * swap deterministically.
 */
@SpringBootTest(properties = {
        "demo.producer.enabled=false",
        "demo.consumer.enabled=false",
        "demo.swap.enabled=false"
})
@Import(TestDatabaseConfig.class)
class PartitionSwapIntegrationTest {

    @Autowired
    IngestEventRepository ingestRepository;

    @Autowired
    ReadEventRepository readRepository;

    @Autowired
    PartitionSwapService swapService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void completedMinutePartitionIsSwappedIntoTheReadTable() {
        // Write into LAST minute's partition — a minute that has already closed, exactly
        // what the scheduler would swap on its next tick. The schema initializer created
        // this partition at startup.
        Instant lastMinute = Instant.now().minus(Duration.ofMinutes(1));
        String partition = PartitionNaming.nameFor(lastMinute);

        List<IngestEvent> batch = IntStream.range(0, 100)
                .mapToObj(i -> new IngestEvent(
                        lastMinute,
                        i % 2 == 0 ? "ORDER_CREATED" : "PAYMENT_CAPTURED",
                        "{\"i\":" + i + "}"))
                .toList();
        ingestRepository.saveAll(batch);

        assertThat(readRepository.count())
                .as("nothing is visible on the read side before the swap")
                .isZero();

        // Pin asOf just past the grace window so last minute's partition always
        // qualifies, even when this test happens to run right at a minute boundary.
        // The current (open) minute stays far outside the cutoff either way.
        Instant graceSafe = PartitionNaming.minuteOf(Instant.now()).plusSeconds(3);
        Instant asOf = graceSafe.isAfter(Instant.now()) ? graceSafe : Instant.now();

        List<String> swapped = swapService.swapCompletedPartitions(asOf);

        assertThat(swapped).contains(partition);

        // The partition physically moved from one parent to the other.
        assertThat(swapService.childrenOf("events"))
                .extracting(ChildPartition::name)
                .contains(partition);
        assertThat(swapService.childrenOf("events_ingest"))
                .extracting(ChildPartition::name)
                .doesNotContain(partition);

        // Every row is now served by the read-side JPA mapping.
        assertThat(readRepository.count()).isEqualTo(100);
        assertThat(readRepository.countByOccurredAtGreaterThanEqual(lastMinute.minusSeconds(1)))
                .isEqualTo(100);

        List<EventTypeCount> byType = readRepository.countByTypeSince(lastMinute.minusSeconds(1));
        assertThat(byType)
                .extracting(EventTypeCount::getEventType, EventTypeCount::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ORDER_CREATED", 50L),
                        org.assertj.core.groups.Tuple.tuple("PAYMENT_CAPTURED", 50L));

        // The read-optimized indexes were built during the swap and got linked to the
        // parent's partitioned indexes on attach.
        List<String> indexes = jdbc.sql(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename = :t")
                .param("t", partition)
                .query(String.class)
                .list();
        assertThat(indexes).contains(partition + "_type_time_idx", partition + "_time_idx");

        // The scaffolding CHECK constraint (used to skip ATTACH's validation scan) is gone.
        Long leftoverChecks = jdbc.sql(
                        "SELECT count(*) FROM pg_constraint WHERE conname = :name")
                .param("name", partition + "_bounds_check")
                .query(Long.class)
                .single();
        assertThat(leftoverChecks).isZero();
    }

    @Test
    void ensureIngestPartitionsIsIdempotentAndCoversTheFuture() {
        Instant now = Instant.now();
        swapService.ensureIngestPartitions(now);
        swapService.ensureIngestPartitions(now); // second call must be a clean no-op

        assertThat(swapService.childrenOf("events_ingest"))
                .extracting(ChildPartition::name)
                .contains(
                        PartitionNaming.nameFor(now),
                        PartitionNaming.nameFor(now.plus(Duration.ofMinutes(1))),
                        PartitionNaming.nameFor(now.plus(Duration.ofMinutes(2))),
                        PartitionNaming.nameFor(now.plus(Duration.ofMinutes(3))));
    }
}
