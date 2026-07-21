package com.example.schemaregistry;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a real Kafka broker and a real Confluent Schema Registry once for the
 * whole test run (the Testcontainers "singleton container" pattern) and points
 * Spring at them.
 *
 * <p>The broker exposes an extra listener, {@code kafka:19092}, on the shared
 * Docker network. The Schema Registry container connects to Kafka over that
 * internal address, while the test JVM talks to both over their mapped host ports.
 */
public abstract class AbstractKafkaSchemaRegistryTest {

    private static final String CONFLUENT_VERSION = "8.0.0";

    static final Network NETWORK = Network.newNetwork();

    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer("confluentinc/cp-kafka:" + CONFLUENT_VERSION)
                    .withListener("kafka:19092")
                    .withNetwork(NETWORK);

    static final GenericContainer<?> SCHEMA_REGISTRY =
            new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:" + CONFLUENT_VERSION))
                    .withNetwork(NETWORK)
                    .withExposedPorts(8081)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                    .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    static {
        KAFKA.start();
        SCHEMA_REGISTRY.start();
    }

    protected static String schemaRegistryUrl() {
        return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
                AbstractKafkaSchemaRegistryTest::schemaRegistryUrl);
    }
}
