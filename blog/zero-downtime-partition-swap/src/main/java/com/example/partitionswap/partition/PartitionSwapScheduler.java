package com.example.partitionswap.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Drives the swap once per minute, a few seconds after the minute boundary.
 *
 * <p>Each run: repair anything a crashed run left behind, make sure future ingest
 * partitions exist, swap the minute(s) that just closed, and drop partitions past the
 * retention window. Every sub-step is idempotent, so overlapping failures self-heal on
 * the next tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.swap.enabled", havingValue = "true", matchIfMissing = true)
public class PartitionSwapScheduler {

    private final PartitionSwapService swapService;
    // Injected for ordering only: guarantees the schema exists before the first tick.
    private final PartitionSchemaInitializer schemaInitializer;

    @Scheduled(cron = "${demo.swap.cron:5 * * * * *}")
    public void tick() {
        Instant asOf = Instant.now();
        long started = System.nanoTime();
        try {
            List<String> adopted = swapService.adoptOrphans();
            swapService.ensureIngestPartitions(asOf);
            List<String> swapped = swapService.swapCompletedPartitions(asOf);
            List<String> dropped = swapService.enforceRetention(asOf);

            log.info("Swap tick done in {} ms — swapped {}, adopted {}, dropped {}",
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    swapped, adopted, dropped);
        } catch (RuntimeException e) {
            // Never let one bad tick kill the schedule; the next tick repairs and retries.
            log.error("Swap tick failed — will retry next minute", e);
        }
    }
}
