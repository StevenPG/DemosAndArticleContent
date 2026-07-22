package com.example.schemaregistry;

import java.util.concurrent.CompletableFuture;

import com.example.schemaregistry.avro.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link OrderEvent} records. The value serializer (configured in
 * application.yml) is Confluent's {@code KafkaAvroSerializer}, which registers
 * the Avro schema with the Schema Registry on first use and prefixes each
 * payload with the returned schema id.
 */
@Component
public class OrderProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topic;

    OrderProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                  @Value("${app.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, OrderEvent>> send(OrderEvent event) {
        // Key by orderId so all events for one order land on the same partition.
        return kafkaTemplate.send(topic, event.getOrderId(), event);
    }
}
