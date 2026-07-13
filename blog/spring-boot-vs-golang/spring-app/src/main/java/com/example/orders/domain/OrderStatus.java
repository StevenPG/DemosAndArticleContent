package com.example.orders.domain;

/**
 * Lifecycle of an order:
 * PENDING (accepted, event published) -> PROCESSING (consumer picked it up)
 * -> COMPLETED (payment charged + stock reserved) or FAILED.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
