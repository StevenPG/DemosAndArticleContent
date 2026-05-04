package com.example.batchguide.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity representing an order that was skipped during batch processing due to a
 * validation failure.
 *
 * <p>Records are inserted into the {@code skipped_orders} table by
 * {@link com.example.batchguide.listener.OrderSkipListener} whenever the processor
 * throws a {@link com.example.batchguide.exception.ValidationException} and the skip
 * limit has not yet been reached.
 *
 * <p>The {@code id} column uses a database-generated sequence ({@code BIGSERIAL}) so
 * multiple skip events for the same order are all preserved with distinct rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skipped_orders")
public class SkippedOrder {

    /** Auto-generated surrogate key — one row per skip event. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** The original order identifier from the CSV that was skipped. */
    @Column(name = "order_id", length = 50)
    private String orderId;

    /** Human-readable explanation of why the order was skipped. */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** Timestamp when the skip event was recorded by the listener. */
    @Column(name = "skipped_at")
    private LocalDateTime skippedAt;
}
