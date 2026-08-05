package com.example.partitionswap.common;

import java.time.Instant;
import java.util.UUID;

/**
 * The Kafka payload: one reading from one device. {@code recordedAt} is the
 * event time stamped by the producer; the ingest service adds its own
 * {@code ingested_at} column at COPY time, and that arrival time — not event
 * time — is the partition key. Partitioning by arrival time means a partition
 * is provably complete the moment its minute (plus a small in-flight grace)
 * has passed, which is what makes the swap safe to automate. Late-arriving
 * events land in the partition of the minute they arrived, with their event
 * time preserved in {@code recordedAt} for queries that care.
 */
public record SensorReadingEvent(
        UUID id,
        String deviceId,
        String metric,
        double reading,
        Instant recordedAt) {
}
