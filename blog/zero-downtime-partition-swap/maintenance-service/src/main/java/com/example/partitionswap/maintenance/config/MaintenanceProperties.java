package com.example.partitionswap.maintenance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param graceSeconds        how long past a minute's end before its staging
 *                            table is considered complete (covers in-flight
 *                            COPY batches and modest clock skew)
 * @param attachLockTimeoutMs lock_timeout for the ATTACH statement — bounds
 *                            how long the attach may sit in the parent's lock
 *                            queue before giving up and retrying
 * @param attachAttempts      how many times to retry an attach that lost the
 *                            lock-timeout race before leaving the table for
 *                            the next scheduler tick
 * @param stalenessThresholdSeconds how far behind promotion may fall before
 *                            the health endpoint reports DOWN. Must exceed
 *                            one minute (the window) plus the grace period
 *                            plus a scheduler tick, or healthy systems will
 *                            flap
 * @param retention           automatic detach+drop of old partitions
 */
@ConfigurationProperties(prefix = "maintenance")
public record MaintenanceProperties(
        int graceSeconds,
        int attachLockTimeoutMs,
        int attachAttempts,
        int stalenessThresholdSeconds,
        Retention retention) {

    public record Retention(boolean enabled, int maxAgeMinutes) {
    }
}
