package com.stevenpg.ecommerce.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published when a new order is successfully persisted.
 * Consumed by the Inventory and Payments modules.
 */
public record OrderPlacedEvent(
        UUID orderId,
        UUID customerId,
        List<LineItem> items,
        BigDecimal totalAmount
) {
    public record LineItem(UUID productId, int quantity, BigDecimal unitPrice) {}
}
