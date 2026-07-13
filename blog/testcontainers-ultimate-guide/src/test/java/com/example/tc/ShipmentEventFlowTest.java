package com.example.tc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The full async flow: publish to a REAL Kafka broker, listener consumes,
 * row updates in a REAL Postgres. This is the test that mocks can't give you.
 */
@SpringBootTest
@Import(ContainersConfig.class)
class ShipmentEventFlowTest {

    @Autowired
    ShipmentRepository repository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void kafkaEventUpdatesShipmentStatus() {
        repository.save(new Shipment("TRACK-KAFKA", "CREATED"));

        kafkaTemplate.send("shipment-events", "TRACK-KAFKA:DELIVERED");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findByTrackingNumber("TRACK-KAFKA"))
                        .isPresent()
                        .get()
                        .extracting(Shipment::getStatus)
                        .isEqualTo("DELIVERED"));
    }
}
