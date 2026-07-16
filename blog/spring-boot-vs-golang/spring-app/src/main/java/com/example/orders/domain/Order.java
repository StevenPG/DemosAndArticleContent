package com.example.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backed by the {@code orders} table (created by Flyway migration
 * V1). "order" is a reserved word in SQL, hence the plural table name.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private String item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // required by JPA
    }

    public Order(String customerEmail, String item, int quantity, long totalCents) {
        this.id = UUID.randomUUID();
        this.customerEmail = customerEmail;
        this.item = item;
        this.quantity = quantity;
        this.totalCents = totalCents;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = OrderStatus.PROCESSING;
    }

    public void markCompleted(String paymentId) {
        this.status = OrderStatus.COMPLETED;
        this.paymentId = paymentId;
    }

    public void markFailed(String reason) {
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
