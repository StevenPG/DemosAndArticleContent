package com.stevenpg.ecommerce.inventory;

import com.stevenpg.ecommerce.orders.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.moments.support.TimeMachine;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the three {@code @ApplicationModuleTest} bootstrap modes:
 *
 * <pre>
 *   STANDALONE           → only inventory beans
 *   DIRECT_DEPENDENCIES  → inventory + orders
 *   ALL_DEPENDENCIES     → inventory + orders + catalog   ← this class
 * </pre>
 *
 * {@code ALL_DEPENDENCIES} loads the entire transitive dependency chain of the module
 * under test.  Here, {@code inventory} depends on {@code orders}, which depends on
 * {@code catalog}, so all three slices are present.  The payments module is NOT loaded.
 *
 * <p>This lets us publish a real {@link OrderPlacedEvent} and verify that
 * {@link com.stevenpg.ecommerce.inventory.internal.OrderPlacedListener} reserves stock
 * correctly — a higher-fidelity test than mocking the listener, but still far cheaper
 * than a full {@code @SpringBootTest}.
 *
 * <p>The class also exercises the <strong>Spring Modulith Moments</strong> module.
 * In production, {@code spring-modulith-moments} fires {@link org.springframework.modulith.moments.DayHasPassed}
 * at midnight.  In tests, we activate {@link TimeMachine} (via
 * {@code spring.modulith.moments.enable-time-machine=true} in {@code test/resources/application.yml})
 * and call {@link TimeMachine#shiftBy} to advance the clock — which publishes the same
 * time-based events without waiting for the scheduler.
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class InventoryModuleTests {

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TimeMachine timeMachine;

    private UUID productId;

    @BeforeEach
    void insertInventoryItem() {
        productId = UUID.randomUUID();
        // Start with 15 units on hand — above the low-stock threshold of 10
        jdbc.update(
            "INSERT INTO inventory_items (id, product_id, quantity_on_hand, quantity_reserved) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), productId, 15, 0
        );
    }

    // -------------------------------------------------------------------------
    // 1. Slice sanity
    // -------------------------------------------------------------------------

    @Test
    void inventoryServiceBeanIsAvailable() {
        assertThat(inventory).isNotNull();
    }

    @Test
    void findByProductIdReturnsItemWhenPresent() {
        assertThat(inventory.findByProductId(productId)).isPresent();
    }

    // -------------------------------------------------------------------------
    // 2. Event-driven stock reservation
    //    Publishes an OrderPlacedEvent directly (as if the orders module fired it)
    //    and waits for the inventory state to reflect the reservation.
    //    This tests the @ApplicationModuleListener inside OrderPlacedListener
    //    end-to-end, including the AFTER_COMMIT transaction boundary.
    // -------------------------------------------------------------------------

    @Test
    void stockIsReservedAfterOrderPlaced(Scenario scenario) {
        int qty = 3;
        var lineItems = List.of(new OrderPlacedEvent.LineItem(productId, qty, new BigDecimal("50.00")));
        var event = new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID(), lineItems, new BigDecimal("150.00"));

        // scenario.publish() commits the event in a REQUIRES_NEW transaction, allowing
        // the @ApplicationModuleListener (AFTER_COMMIT) to fire in its own transaction.
        //
        // andWaitForStateChange(Supplier, Predicate) polls until the predicate passes.
        // IMPORTANT: Always supply an explicit Predicate — the default acceptance (any
        // non-null value) would immediately accept the initial 0 and stop polling.
        scenario.publish(event)
            .andWaitForStateChange(
                () -> inventory.findByProductId(productId).map(InventoryItem::getQuantityReserved).orElse(0),
                reserved -> reserved >= qty   // wait until the listener has actually reserved the units
            )
            .andVerify(reserved -> assertThat(reserved).isEqualTo(qty));
    }

    @Test
    void stockShortageEventPublishedWhenQuantityExceedsStock(Scenario scenario) {
        var lineItems = List.of(new OrderPlacedEvent.LineItem(productId, 100, new BigDecimal("50.00")));
        var orderEvent = new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID(), lineItems, new BigDecimal("5000.00"));

        scenario.publish(orderEvent)
            .andWaitForEventOfType(StockShortageEvent.class)
            .toArriveAndVerify(e -> assertThat(e.productId()).isEqualTo(productId));
    }

    // -------------------------------------------------------------------------
    // 3. Moments / TimeMachine integration
    //    TimeMachine is enabled in test/resources/application.yml via:
    //      spring.modulith.moments.enable-time-machine: true
    //    This registers a TimeMachine bean instead of Moments, exposing a public
    //    shiftBy() method.  Wrapping it in scenario.stimulate() ensures the
    //    DayHasPassed event is published inside a transaction so that the
    //    @ApplicationModuleListener (AFTER_COMMIT) can fire in its own transaction.
    // -------------------------------------------------------------------------

    @Test
    void lowStockWarningPublishedWhenInventoryBelowThreshold(Scenario scenario) {
        // Reduce stock below the audit threshold of 10
        jdbc.update("UPDATE inventory_items SET quantity_on_hand = 3 WHERE product_id = ?", productId);

        // shiftBy(1 day) publishes DayHasPassed, which triggers DailyInventoryAuditListener.
        // The listener scans all items and publishes LowStockWarningEvent for the low-stock one.
        scenario.stimulate(() -> timeMachine.shiftBy(Duration.ofDays(1)))
            .andWaitForEventOfType(LowStockWarningEvent.class)
            .toArriveAndVerify(warning -> {
                assertThat(warning.productId()).isEqualTo(productId);
                assertThat(warning.availableQty()).isLessThan(10);
            });
    }

    @Test
    void noLowStockWarningWhenStockIsAboveThreshold(Scenario scenario) {
        // Stock is 15 — above the threshold of 10, so no warning should be emitted for this product.
        // We give the audit a chance to run and then assert the event was NOT published.
        // The matching() filter scopes the absence-check to our specific product.
        scenario.stimulate(() -> timeMachine.shiftBy(Duration.ofDays(1)))
            .andWaitForStateChange(
                () -> inventory.findByProductId(productId).isPresent(),
                wasFound -> wasFound   // poll until the item is confirmed present (sanity check)
            )
            .andVerifyEvents(events ->
                assertThat(events.ofType(LowStockWarningEvent.class)
                    .matching(e -> e.productId().equals(productId)))
                    .isEmpty()
            );
    }
}
