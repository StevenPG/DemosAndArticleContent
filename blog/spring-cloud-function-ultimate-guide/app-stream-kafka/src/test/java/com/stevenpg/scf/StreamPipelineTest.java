package com.stevenpg.scf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of the Kafka surface against a REAL broker (Testcontainers).
 *
 * <p>It proves two things the messaging surface adds on top of the plain
 * functions:
 * <ol>
 *   <li>a valid order sent to {@code orders} comes out as a {@code Decision} on
 *       {@code decisions} — the same enrich|validate pipeline, now over Kafka;</li>
 *   <li>a poison order (unsupported currency) is diverted to the
 *       {@code orders-dlq} dead-letter topic instead of blocking the partition.</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@EnabledIf("dockerAvailable")   // skip (don't fail) when there is no Docker daemon
class StreamPipelineTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.0");

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void validOrderFlowsToDecisionsTopic() {
        send("orders", "{\"orderId\":\"ord-1\",\"customerId\":\"cust-alice\","
                + "\"amount\":199.99,\"currency\":\"USD\",\"itemCount\":2}");

        JsonNode decision = awaitRecord("decisions", "scf-test-decisions");

        assertThat(decision.get("orderId").asText()).isEqualTo("ord-1");
        assertThat(decision.get("outcome").asText())
                .isIn("APPROVED", "REJECTED", "REVIEW");
    }

    @Test
    void poisonOrderIsRoutedToDeadLetterTopic() {
        send("orders", "{\"orderId\":\"ord-eur\",\"customerId\":\"cust-bob\","
                + "\"amount\":50.00,\"currency\":\"EUR\",\"itemCount\":1}");

        JsonNode dlq = awaitRecord("orders-dlq", "scf-test-dlq");

        assertThat(dlq.get("orderId").asText()).isEqualTo("ord-eur");
        assertThat(dlq.get("currency").asText()).isEqualTo("EUR");
    }

    // ---- tiny raw Kafka helpers so the test does not depend on the binder ----

    private void send(String topic, String json) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, json));
            producer.flush();
        }
    }

    private JsonNode awaitRecord(String topic, String group) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        AtomicReference<JsonNode> found = new AtomicReference<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && !record.value().isBlank()) {
                        found.set(JSON.readTree(record.value()));
                        return;
                    }
                }
                assertThat(found.get()).as("a record on topic %s", topic).isNotNull();
            });
        }
        return found.get();
    }
}
