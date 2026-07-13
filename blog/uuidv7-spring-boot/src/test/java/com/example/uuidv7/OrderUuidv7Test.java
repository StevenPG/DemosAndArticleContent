package com.example.uuidv7;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JPA side of the article: Hibernate's VERSION_7 style generates a
 * real UUIDv7 and the ID is assigned at persist() time (no DB round trip).
 */
@SpringBootTest
@Testcontainers
class OrderUuidv7Test {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    OrderRepository orders;

    @Test
    void generatesVersion7UuidAtPersistTime() {
        Order saved = orders.save(new Order("steve@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
    }

    @Test
    void idsAreMonotonicallyIncreasingAcrossInserts() {
        Order first = orders.save(new Order("a@example.com"));
        Order second = orders.save(new Order("b@example.com"));

        // UUIDv7 sorts by creation time — the whole point of the article.
        assertThat(second.getId().toString()).isGreaterThan(first.getId().toString());
    }
}
