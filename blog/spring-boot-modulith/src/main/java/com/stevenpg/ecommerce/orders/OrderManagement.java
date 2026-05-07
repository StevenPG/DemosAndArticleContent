package com.stevenpg.ecommerce.orders;

import com.stevenpg.ecommerce.catalog.CatalogService;
import com.stevenpg.ecommerce.catalog.Product;
import com.stevenpg.ecommerce.orders.internal.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderManagement {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final ApplicationEventPublisher events;

    OrderManagement(OrderRepository orders, CatalogService catalog, ApplicationEventPublisher events) {
        this.orders = orders;
        this.catalog = catalog;
        this.events = events;
    }

    public Order placeOrder(PlaceOrderCommand command) {
        var order = new Order(command.customerId());

        for (var item : command.items()) {
            Product product = catalog.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.productId()));
            order.addItem(new OrderItem(product.getId(), product.getName(), item.quantity(), product.getPrice()));
        }

        orders.save(order);

        List<OrderPlacedEvent.LineItem> lineItems = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.LineItem(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        events.publishEvent(new OrderPlacedEvent(order.getId(), order.getCustomerId(), lineItems, order.total()));

        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return orders.findById(id);
    }

    public void confirm(UUID orderId) {
        orders.findById(orderId).ifPresent(Order::confirm);
    }

    public void markPaymentFailed(UUID orderId) {
        orders.findById(orderId).ifPresent(Order::markPaymentFailed);
    }

    public void cancel(UUID orderId) {
        orders.findById(orderId).ifPresent(Order::cancel);
    }
}
