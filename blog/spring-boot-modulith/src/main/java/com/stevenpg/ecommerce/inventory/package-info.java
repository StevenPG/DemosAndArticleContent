/**
 * Public API of the Inventory module.
 *
 * Tracks stock levels per product. Listens for {@link com.stevenpg.ecommerce.orders.OrderPlacedEvent}
 * and publishes {@link com.stevenpg.ecommerce.inventory.StockReservedEvent} or
 * {@link com.stevenpg.ecommerce.inventory.StockShortageEvent} in response.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {"orders"}
)
package com.stevenpg.ecommerce.inventory;
