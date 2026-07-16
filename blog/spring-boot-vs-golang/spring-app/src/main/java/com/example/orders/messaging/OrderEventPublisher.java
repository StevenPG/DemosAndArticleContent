package com.example.orders.messaging;

import com.example.orders.config.AppKafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka producer. KafkaTemplate is auto-configured from the spring.kafka.*
 * properties (bootstrap servers, JSON value serializer); publishing is one
 * line. The order id is the record key so all events for one order stay on
 * one partition, preserving their order.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String SOURCE = "spring-order-service";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final AppKafkaProperties properties;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                               AppKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishOrderCreated(UUID orderId) {
        OrderEvent event = OrderEvent.created(orderId, SOURCE);
        kafkaTemplate.send(properties.ordersTopic(), orderId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}", event.type(), orderId, ex);
                    } else {
                        log.debug("Published {} for order {} to {}-{}@{}",
                                event.type(), orderId,
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
