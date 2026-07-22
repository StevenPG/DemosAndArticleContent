package com.example.schemaregistry;

import com.example.schemaregistry.avro.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link OrderEvent} records. The value deserializer is Confluent's
 * {@code KafkaAvroDeserializer} with {@code specific.avro.reader=true}, so it
 * reads the schema id from the payload, fetches the writer schema from the
 * registry, and projects the bytes onto our generated {@link OrderEvent} class.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderEventStore store;

    OrderEventListener(OrderEventStore store) {
        this.store = store;
    }

    @KafkaListener(topics = "${app.topic}", groupId = "${spring.kafka.consumer.group-id}")
    void onOrderEvent(OrderEvent event) {
        log.info("Consumed order {} for customer {}: {} {} ({})",
                event.getOrderId(),
                event.getCustomerId(),
                event.getAmount(),
                event.getCurrency(),
                event.getStatus());
        store.add(event);
    }
}
