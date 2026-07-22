package com.example.schemaregistry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.example.schemaregistry.avro.OrderEvent;
import com.example.schemaregistry.avro.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the full round trip against real infrastructure: the producer serializes
 * an {@link OrderEvent} with Avro, the schema is registered with the Schema
 * Registry, and the listener deserializes it back into the generated class.
 */
@SpringBootTest
class OrderFlowIntegrationTest extends AbstractKafkaSchemaRegistryTest {

    @Autowired
    OrderProducer producer;

    @Autowired
    OrderEventStore store;

    @Test
    void producesAndConsumesThroughSchemaRegistry() {
        store.clear();

        OrderEvent event = OrderEvent.newBuilder()
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId("customer-42")
                .setAmount(129.99)
                .setCurrency("USD")
                .setStatus(OrderStatus.PLACED)
                .setCreatedAt(Instant.now())
                .build();

        producer.send(event).join();

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(store.size()).isEqualTo(1));

        OrderEvent consumed = store.all().get(0);
        assertThat(consumed.getOrderId()).isEqualTo(event.getOrderId());
        assertThat(consumed.getCustomerId()).isEqualTo("customer-42");
        assertThat(consumed.getAmount()).isEqualTo(129.99);
        assertThat(consumed.getStatus()).isEqualTo(OrderStatus.PLACED);
    }
}
