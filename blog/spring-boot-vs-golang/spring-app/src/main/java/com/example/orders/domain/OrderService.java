package com.example.orders.domain;

import com.example.orders.clients.InventoryClient;
import com.example.orders.clients.PaymentClient;
import com.example.orders.messaging.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The business core. Order creation persists and publishes an event;
 * processing (triggered by the Kafka consumer) calls the two downstream
 * services with OAuth2 client-credentials tokens and settles the final status.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository repository,
                        PaymentClient paymentClient,
                        InventoryClient inventoryClient,
                        OrderEventPublisher eventPublisher) {
        this.repository = repository;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order createOrder(String customerEmail, String item, int quantity, long totalCents) {
        Order order = repository.save(new Order(customerEmail, item, quantity, totalCents));
        eventPublisher.publishOrderCreated(order.getId());
        log.info("Created order {} for {}", order.getId(), customerEmail);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(OrderStatus status) {
        return status == null
                ? repository.findAll()
                : repository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Invoked by the Kafka consumer. Both apps share the {@code order-events}
     * topic, so events for orders created by the Go app land here too — those
     * IDs are unknown to this database and are skipped on purpose.
     */
    @Transactional
    public void processOrder(UUID orderId) {
        Order order = repository.findById(orderId).orElse(null);
        if (order == null) {
            log.debug("Ignoring event for order {} not present in this service's database", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("Order {} already {} — skipping duplicate event", orderId, order.getStatus());
            return;
        }

        order.markProcessing();
        try {
            var payment = paymentClient.charge(order.getId(), order.getTotalCents());
            inventoryClient.reserve(order.getId(), order.getItem(), order.getQuantity());
            order.markCompleted(payment.paymentId());
            log.info("Order {} completed (payment {})", order.getId(), payment.paymentId());
        } catch (Exception e) {
            order.markFailed(e.getMessage());
            log.warn("Order {} failed: {}", order.getId(), e.getMessage());
        }
    }
}
