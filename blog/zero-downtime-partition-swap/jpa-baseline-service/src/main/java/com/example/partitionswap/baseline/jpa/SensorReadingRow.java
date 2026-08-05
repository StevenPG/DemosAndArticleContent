package com.example.partitionswap.baseline.jpa;

import com.example.partitionswap.common.SensorReadingEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * The baseline entity: an ordinary JPA mapping over an ordinary flat table.
 *
 * <p>The {@link Persistable} implementation is the interesting part, and it is
 * where the most expensive mistake in JPA ingestion hides.
 *
 * <p>Spring Data's {@code save()}/{@code saveAll()} has to decide between
 * {@code persist()} and {@code merge()}. With a database-generated id that is
 * easy — a null id means new. But this entity's id is <b>assigned</b> (a
 * UUIDv7 minted by the producer, because it identifies the event across Kafka),
 * so the id is never null, so the default check concludes the row already
 * exists and calls {@code merge()}. Merge means Hibernate must first load the
 * current state: <b>one SELECT per row, before every INSERT</b>.
 *
 * <p>Nothing about the code looks wrong. {@code saveAll(tenThousandRows)}
 * quietly becomes ten thousand SELECTs followed by ten thousand INSERTs, and
 * the usual reaction is to blame the database. Implementing
 * {@code Persistable} and returning {@code true} tells Spring Data to call
 * {@code persist()} directly, halving the statement count.
 *
 * <p>This class supports both behaviours so the benchmark can measure the
 * difference honestly: the {@code naive} profile leaves {@code isNew} false
 * (identical to not implementing {@code Persistable} at all — the default any
 * developer gets), and the {@code tuned} profile sets it true.
 */
@Entity
@Table(name = "sensor_readings_jpa")
public class SensorReadingRow implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private double reading;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    /** Never persisted; only steers the persist-versus-merge decision. */
    @Transient
    private boolean isNew;

    protected SensorReadingRow() {
    }

    public SensorReadingRow(SensorReadingEvent event, Instant ingestedAt, boolean isNew) {
        this.id = event.id();
        this.deviceId = event.deviceId();
        this.metric = event.metric();
        this.reading = event.reading();
        this.recordedAt = event.recordedAt();
        this.ingestedAt = ingestedAt;
        this.isNew = isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
