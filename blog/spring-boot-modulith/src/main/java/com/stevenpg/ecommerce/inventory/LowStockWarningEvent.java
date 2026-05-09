package com.stevenpg.ecommerce.inventory;

import java.util.UUID;

/**
 * Published by the daily inventory audit whenever a product's available stock drops
 * below the configured threshold.  Downstream modules (e.g. a future procurement module)
 * can listen for this event to trigger restocking workflows.
 */
public record LowStockWarningEvent(UUID productId, int availableQty) {}
