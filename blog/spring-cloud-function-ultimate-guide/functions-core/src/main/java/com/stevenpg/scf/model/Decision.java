package com.stevenpg.scf.model;

import java.math.BigDecimal;

/**
 * The pipeline's verdict on an order.
 *
 * @param outcome one of {@link #APPROVED}, {@link #REJECTED}, {@link #REVIEW}.
 *                It doubles as the routing key used by the routing function and
 *                by the Kafka DLQ / branching logic in the stream module.
 */
public record Decision(
        String orderId,
        String outcome,
        String reason,
        BigDecimal amount,
        String customerTier) {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REVIEW = "REVIEW";
}
