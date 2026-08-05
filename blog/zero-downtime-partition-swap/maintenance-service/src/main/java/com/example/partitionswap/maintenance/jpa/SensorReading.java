package com.example.partitionswap.maintenance.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-model entity mapped over the partitioned PARENT table. This is the
 * payoff of the whole design: JPA (and every other reader) queries one
 * logical table and never learns that partitions are appearing underneath it
 * once a minute — the swap is invisible at this layer.
 *
 * <p>{@code @Immutable} because rows arrive exclusively through COPY; Hibernate
 * should never dirty-check or flush these. The composite id mirrors the
 * table's primary key, which must include the partition key
 * ({@code ingested_at}) — a rule partitioned tables impose on every unique
 * constraint.
 */
@Entity
@Immutable
@Table(name = "sensor_readings")
@IdClass(SensorReadingId.class)
public class SensorReading {

    @Id
    private UUID id;

    @Id
    @Column(name = "ingested_at")
    private Instant ingestedAt;

    @Column(name = "device_id")
    private String deviceId;

    private String metric;

    private double reading;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    protected SensorReading() {
    }

    public UUID getId() {
        return id;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getMetric() {
        return metric;
    }

    public double getReading() {
        return reading;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
