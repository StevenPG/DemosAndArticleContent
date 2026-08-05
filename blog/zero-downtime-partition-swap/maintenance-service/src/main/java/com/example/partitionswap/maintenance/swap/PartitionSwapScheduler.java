package com.example.partitionswap.maintenance.swap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires a few seconds after every minute boundary: the previous minute's
 * staging table stops receiving writes at :00, the grace period covers any
 * COPY still in flight, and by :10 the table is provably complete and safe to
 * index and attach. The swap work itself is deliberately NOT in the ingest
 * service — index builds are CPU- and I/O-hungry, and isolating them in a
 * separate process means a slow index build or a lock-queue retry can never
 * apply backpressure to the Kafka consumer.
 */
@Component
public class PartitionSwapScheduler {

    private static final Logger log = LoggerFactory.getLogger(PartitionSwapScheduler.class);

    private final PartitionSwapService swapService;

    public PartitionSwapScheduler(PartitionSwapService swapService) {
        this.swapService = swapService;
    }

    @Scheduled(cron = "${maintenance.swap-cron:10 * * * * *}")
    public void promoteCompletedMinutes() {
        int promoted = swapService.promoteEligibleStagingTables();
        if (promoted == 0) {
            log.debug("no staging tables eligible this tick");
        }
    }
}
