package com.stevenpg.ecommerce.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// DIRECT_DEPENDENCIES loads the catalog module alongside orders, satisfying CatalogService injection
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class OrderModuleTests {

    @Autowired
    OrderManagement orders;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newOrderStartsInPendingState() {
        var order = new Order(UUID.randomUUID());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void orderTotalSumsLineItems() {
        var order = new Order(UUID.randomUUID());
        order.addItem(new OrderItem(UUID.randomUUID(), "Widget", 2, new BigDecimal("10.00")));
        order.addItem(new OrderItem(UUID.randomUUID(), "Gadget", 1, new BigDecimal("15.00")));
        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    void cancelledOrderHasCancelledStatus() {
        var order = new Order(UUID.randomUUID());
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void orderManagementBeanIsAvailable() {
        assertThat(orders).isNotNull();
    }

    @Test
    void placeOrderPublishesOrderPlacedEvent(AssertablePublishedEvents events) {
        var productId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO products (id, name, description, price, sku) VALUES (?, ?, ?, ?, ?)",
            productId, "Test Widget", "Widget for order module testing", new BigDecimal("25.00"),
            "WIDGET-" + productId
        );

        var customerId = UUID.randomUUID();
        var command = new PlaceOrderCommand(customerId, List.of(new PlaceOrderCommand.Item(productId, 2)));

        orders.placeOrder(command);

        assertThat(events)
            .contains(OrderPlacedEvent.class)
            .matching(e -> e.customerId().equals(customerId));
    }
}
