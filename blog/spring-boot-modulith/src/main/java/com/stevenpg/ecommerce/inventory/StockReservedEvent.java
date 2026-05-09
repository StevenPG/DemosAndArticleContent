package com.stevenpg.ecommerce.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when all line items in an order have been successfully reserved.
 */
public record StockReservedEvent(UUID orderId, BigDecimal orderTotal) {}
