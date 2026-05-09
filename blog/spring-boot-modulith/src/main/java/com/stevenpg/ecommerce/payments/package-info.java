/**
 * Public API of the Payments module.
 *
 * Processes payments in response to {@link com.stevenpg.ecommerce.inventory.StockReservedEvent}.
 * Updates order status by calling {@link com.stevenpg.ecommerce.orders.OrderManagement} directly,
 * then publishes {@link com.stevenpg.ecommerce.payments.PaymentCompletedEvent} or
 * {@link com.stevenpg.ecommerce.payments.PaymentFailedEvent} for any external consumers.
 *
 * Module dependency graph (acyclic):
 * catalog ← orders ← inventory ← payments → orders
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Payments",
        allowedDependencies = {"inventory", "orders"}
)
package com.stevenpg.ecommerce.payments;
