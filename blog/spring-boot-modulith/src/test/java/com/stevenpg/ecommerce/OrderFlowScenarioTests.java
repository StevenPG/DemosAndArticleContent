package com.stevenpg.ecommerce;

import com.stevenpg.ecommerce.orders.Order;
import com.stevenpg.ecommerce.orders.OrderManagement;
import com.stevenpg.ecommerce.orders.OrderStatus;
import com.stevenpg.ecommerce.orders.PlaceOrderCommand;
import com.stevenpg.ecommerce.payments.PaymentCompletedEvent;
import com.stevenpg.ecommerce.payments.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end scenario tests for the full order processing flow.
 *
 * Uses the Spring Modulith {@link Scenario} API to drive async, event-driven
 * interactions across all four modules (catalog → orders → inventory → payments)
 * within a single {@code @SpringBootTest} context.
 *
 * Demonstrates two complementary assertion styles available on {@link Scenario}:
 *
 * <ul>
 *   <li>{@code andWaitForEventOfType} — Awaitility polls until a specific domain
 *       event is captured; the final {@code toArriveAndVerify(BiConsumer)} receives
 *       both the event and the stimulus return value.</li>
 *   <li>{@code andWaitForStateChange} — Awaitility polls a {@code Supplier} until
 *       a condition is met; use an {@link AtomicReference} to carry the stimulus
 *       result into the polling lambda when you need to look up state by the ID
 *       returned from the action.</li>
 * </ul>
 *
 * Each test seeds its own isolated product and inventory data with random UUIDs
 * so tests are fully independent.
 */
@SpringBootTest
@EnableScenarios
class OrderFlowScenarioTests {

    @Autowired
    OrderManagement orders;

    @Autowired
    JdbcTemplate jdbc;

    private UUID approvedProductId;
    private UUID declinedProductId;

    @BeforeEach
    void insertTestProducts() {
        approvedProductId = UUID.randomUUID();
        declinedProductId = UUID.randomUUID();

        // Price NOT ending in .99 → simulated payment gateway approves
        jdbc.update(
            "INSERT INTO products (id, name, description, price, sku) VALUES (?, ?, ?, ?, ?)",
            approvedProductId, "Pro Headphones", "Studio-quality headphones", new BigDecimal("200.00"),
            "HEADPHONES-" + approvedProductId
        );
        jdbc.update(
            "INSERT INTO inventory_items (id, product_id, quantity_on_hand, quantity_reserved) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), approvedProductId, 20, 0
        );

        // Price ending in .99 → payment gateway always declines (demo rule in PaymentService)
        jdbc.update(
            "INSERT INTO products (id, name, description, price, sku) VALUES (?, ?, ?, ?, ?)",
            declinedProductId, "Budget Cable", "Always-declined demo item", new BigDecimal("9.99"),
            "CABLE-" + declinedProductId
        );
        jdbc.update(
            "INSERT INTO inventory_items (id, product_id, quantity_on_hand, quantity_reserved) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), declinedProductId, 20, 0
        );
    }

    // -------------------------------------------------------------------------
    // 1. andWaitForEventOfType — assert that a downstream domain event fires
    //    The BiConsumer variant gives you the event AND the placeOrder() return
    //    value, so you can cross-verify ids without extra lookups.
    // -------------------------------------------------------------------------

    @Test
    void successfulOrderFlowPublishesPaymentCompletedEvent(Scenario scenario) {
        var command = new PlaceOrderCommand(
            UUID.randomUUID(),
            List.of(new PlaceOrderCommand.Item(approvedProductId, 1))
        );

        // stimulate() commits placeOrder() in a REQUIRES_NEW transaction, which causes:
        //   OrderPlacedEvent → StockReservedEvent → PaymentCompletedEvent
        // to propagate across three @ApplicationModuleListener transactions.
        // Awaitility polls until PaymentCompletedEvent arrives (default 10-second budget).
        scenario.stimulate(() -> orders.placeOrder(command))
            .andWaitForEventOfType(PaymentCompletedEvent.class)
            .toArriveAndVerify((event, order) ->
                assertThat(event.orderId()).isEqualTo(order.getId())
            );
    }

    @Test
    void orderWithDeclinedPaymentPublishesPaymentFailedEvent(Scenario scenario) {
        var command = new PlaceOrderCommand(
            UUID.randomUUID(),
            List.of(new PlaceOrderCommand.Item(declinedProductId, 1))
        );

        scenario.stimulate(() -> orders.placeOrder(command))
            .andWaitForEventOfType(PaymentFailedEvent.class)
            .toArriveAndVerify((event, order) ->
                assertThat(event.orderId()).isEqualTo(order.getId())
            );
    }

    // -------------------------------------------------------------------------
    // 2. andWaitForStateChange — poll the database until the expected state lands
    //    Use this when you care about persisted state, not which events fired.
    //    Because the Supplier passed to andWaitForStateChange() is evaluated after
    //    the stimulus runs, capturing the stimulus result in an AtomicReference lets
    //    you query by the ID of the entity that was just created.
    // -------------------------------------------------------------------------

    @Test
    void confirmedOrderStatusReflectedInDatabase(Scenario scenario) {
        var command = new PlaceOrderCommand(
            UUID.randomUUID(),
            List.of(new PlaceOrderCommand.Item(approvedProductId, 1))
        );

        // Capture the placed Order so the polling supplier can query by its ID.
        var placed = new AtomicReference<Order>();

        scenario.stimulate(() -> {
            var order = orders.placeOrder(command);
            placed.set(order);
            return order;
        })
        .andWaitForStateChange(
            // Supplier: reload the order from the DB on each poll tick
            () -> orders.findById(placed.get().getId())
                        .map(Order::getStatus)
                        .orElse(OrderStatus.PENDING),
            // Acceptance predicate: stop polling once the order is CONFIRMED
            status -> status == OrderStatus.CONFIRMED
        )
        .andVerify(finalStatus ->
            assertThat(finalStatus).isEqualTo(OrderStatus.CONFIRMED)
        );
    }

    @Test
    void failedPaymentOrderStatusReflectedInDatabase(Scenario scenario) {
        var command = new PlaceOrderCommand(
            UUID.randomUUID(),
            List.of(new PlaceOrderCommand.Item(declinedProductId, 1))
        );

        var placed = new AtomicReference<Order>();

        scenario.stimulate(() -> {
            var order = orders.placeOrder(command);
            placed.set(order);
            return order;
        })
        .andWaitForStateChange(
            () -> orders.findById(placed.get().getId())
                        .map(Order::getStatus)
                        .orElse(OrderStatus.PENDING),
            status -> status == OrderStatus.PAYMENT_FAILED
        )
        .andVerify(finalStatus ->
            assertThat(finalStatus).isEqualTo(OrderStatus.PAYMENT_FAILED)
        );
    }
}
