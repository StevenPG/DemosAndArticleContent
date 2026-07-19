package com.example.orders.messaging;

import com.example.orders.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer. @KafkaListener handles the poll loop, JSON deserialization,
 * offset commits, and rebalancing; the method body is just business logic.
 * The consumer group id comes from spring.kafka.consumer.group-id, so the
 * Spring and Go services each get their own copy of every event.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${app.kafka.orders-topic}")
    public void onOrderEvent(OrderEvent event) {
        log.info("Received {} for order {} from {}", event.type(), event.orderId(), event.source());
        if (OrderEvent.ORDER_CREATED.equals(event.type())) {
            orderService.processOrder(event.orderId());
        }
    }
}
