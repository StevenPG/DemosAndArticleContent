package com.stevenpg.ecommerce.inventory;

import java.util.UUID;

/**
 * Published when at least one product in an order cannot be reserved due to insufficient stock.
 */
public record StockShortageEvent(UUID orderId, UUID productId) {}
