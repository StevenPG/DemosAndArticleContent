/**
 * Public API of the Orders module.
 *
 * Entry point for placing and querying orders. This module owns the
 * {@link com.stevenpg.ecommerce.orders.OrderPlacedEvent} which triggers the
 * downstream Inventory and Payments workflows.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Orders",
        allowedDependencies = {"catalog"}
)
package com.stevenpg.ecommerce.orders;
