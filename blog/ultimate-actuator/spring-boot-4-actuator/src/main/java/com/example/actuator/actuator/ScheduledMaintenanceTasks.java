package com.example.actuator.actuator;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks exist purely so the {@code /actuator/scheduledtasks} endpoint has
 * something to list. It reports the cron/fixed-rate/fixed-delay triggers along with
 * the target method for each.
 */
@Slf4j
@Component
public class ScheduledMaintenanceTasks {

    @Scheduled(fixedRate = 60_000)
    @Observed(name = "maintenance.heartbeat")
    public void heartbeat() {
        log.debug("Maintenance heartbeat tick");
    }

    @Scheduled(cron = "0 0 3 * * *") // 03:00 every day
    public void nightlyCleanup() {
        log.info("Running nightly cleanup");
    }
}
