package com.example.partitionswap.maintenance.jpa;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Composite key (id, ingested_at) — partitioned tables require the partition key in every unique constraint. */
public class SensorReadingId implements Serializable {

    private UUID id;
    private Instant ingestedAt;

    protected SensorReadingId() {
    }

    public SensorReadingId(UUID id, Instant ingestedAt) {
        this.id = id;
        this.ingestedAt = ingestedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SensorReadingId that
                && Objects.equals(id, that.id)
                && Objects.equals(ingestedAt, that.ingestedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ingestedAt);
    }
}
