package com.example.partitionswap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Write-side entity, mapped to the hot ingest table {@code events_ingest}.
 *
 * <p>The database table is range-partitioned by {@code occurred_at} into per-minute
 * children and carries only its primary key index, keeping the insert path cheap.
 * PostgreSQL routes each row to the correct minute partition automatically.
 *
 * <p>The database primary key is the composite {@code (id, occurred_at)} — PostgreSQL
 * requires the partition key inside every unique constraint — but {@code id} alone is
 * globally unique (one identity sequence for the whole partition tree), so mapping only
 * {@code id} as the JPA identifier is safe and keeps the entity simple.
 */
@Entity
@Table(name = "events_ingest")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IngestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String payload;

    public IngestEvent(Instant occurredAt, String eventType, String payload) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.payload = payload;
    }
}
