package com.stevenpg.scf.model;

import java.math.BigDecimal;

/**
 * An {@link Order} after the {@code enrichOrder} step has added the data later
 * stages need: the customer's tier and a computed risk score.
 *
 * <p>This is a different type from {@link Order} on purpose — it lets the
 * pipeline be strongly typed end to end: {@code enrichOrder} is
 * {@code Function<Order, EnrichedOrder>}, and {@code routeDecision} consumes an
 * {@code EnrichedOrder}, so the compiler enforces the ordering of the pipeline.
 */
public record EnrichedOrder(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        int itemCount,
        String customerTier,
        int riskScore) {

    /** Convenience factory that carries the original order fields forward. */
    public static EnrichedOrder from(Order order, String customerTier, int riskScore) {
        return new EnrichedOrder(
                order.orderId(),
                order.customerId(),
                order.amount(),
                order.currency(),
                order.itemCount(),
                customerTier,
                riskScore);
    }
}
