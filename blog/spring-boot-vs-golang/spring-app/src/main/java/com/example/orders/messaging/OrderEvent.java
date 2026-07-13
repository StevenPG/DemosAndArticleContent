package com.example.orders.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON payload on the shared {@code order-events} topic. Both the Spring and
 * Go services produce and consume this exact shape.
 */
public record OrderEvent(UUID orderId, String type, String source, Instant occurredAt) {

    public static final String ORDER_CREATED = "ORDER_CREATED";

    public static OrderEvent created(UUID orderId, String source) {
        return new OrderEvent(orderId, ORDER_CREATED, source, Instant.now());
    }
}
