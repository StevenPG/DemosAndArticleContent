package com.example.partitionswap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Read-side entity, mapped to the {@code events} read table.
 *
 * <p>Rows never arrive here through JPA — whole minute partitions are attached by
 * {@link com.example.partitionswap.partition.PartitionSwapService} after their
 * read-optimized indexes have been built. The entity is {@link Immutable}: Hibernate
 * skips dirty checking entirely and rejects accidental writes through this mapping.
 */
@Entity
@Immutable
@Table(name = "events")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadEvent {

    @Id
    private Long id;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "event_type")
    private String eventType;

    private String payload;
}
