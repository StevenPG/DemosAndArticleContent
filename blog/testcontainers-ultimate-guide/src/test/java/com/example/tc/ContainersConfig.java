package com.example.tc;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Shared container definitions using Spring Boot's @ServiceConnection — no
 * @DynamicPropertySource boilerplate. Every test importing this config reuses
 * the same containers within a JVM, and `.withReuse(true)` keeps them alive
 * BETWEEN Gradle runs when testcontainers.reuse.enable=true is set in
 * ~/.testcontainers.properties (see README for the CI timing comparison).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18")
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:3.9.0")
                .withReuse(true);
    }
}
