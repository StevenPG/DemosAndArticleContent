package com.example.batchguide.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a fully validated and enriched order stored in the
 * {@code orders} table.
 *
 * <p>This is the <em>output</em> type of the batch processor.  An {@link OrderRecord}
 * is read from CSV, validated, enriched with the customer name, and mapped to this
 * entity before being written to the database.
 *
 * <p>The writer uses an upsert ({@code ON CONFLICT (id) DO UPDATE}) strategy, so
 * re-running the job with the same CSV is idempotent.
 *
 * <p>Lombok {@code @Data} generates getters/setters required by
 * {@code BeanPropertyItemSqlParameterSourceProvider} for the JDBC batch writer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    /** Primary key — matches the raw CSV {@code id} column. */
    @Id
    @Column(name = "id", length = 50)
    private String id;

    /** Foreign key to the customer dimension, sourced from the CSV. */
    @Column(name = "customer_id", length = 50)
    private String customerId;

    /**
     * Human-readable customer name resolved during processing by looking up
     * {@code customerId} in the in-memory customer registry.
     */
    @Column(name = "customer_name", length = 100)
    private String customerName;

    /** Product SKU or reference code sourced from the CSV. */
    @Column(name = "product_code", length = 50)
    private String productCode;

    /** Monetary value of the order with 12 integer digits and 2 decimal places. */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Order date as a plain string (ISO-8601) sourced directly from the CSV.
     * Stored as a string to avoid time-zone conversion surprises during import.
     */
    @Column(name = "order_date", length = 20)
    private String orderDate;

    /**
     * Timestamp set by the database ({@code NOW()}) at insert time.
     * Updated values are not touched on conflict — see the upsert SQL in
     * {@link com.example.batchguide.writer.OrderItemWriter}.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
