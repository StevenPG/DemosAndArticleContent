package com.example.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** All 10 migrations apply cleanly against PostgreSQL 18 and land on the expected schema. */
@SpringBootTest
@Testcontainers
class MigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    JdbcClient jdbc;

    @Test
    void schemaReachesFinalShape() {
        Integer applied = jdbc.sql(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success")
                .query(Integer.class).single();
        assertThat(applied).isEqualTo(10);

        // Final shape: name is gone, split columns exist, money is cents
        assertThat(columnExists("customers", "name")).isFalse();
        assertThat(columnExists("customers", "first_name")).isTrue();
        assertThat(columnExists("orders", "total_cents")).isTrue();
        assertThat(columnExists("orders", "total")).isFalse();
    }

    private boolean columnExists(String table, String column) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM information_schema.columns
                                       WHERE table_name = :t AND column_name = :c)
                        """)
                .param("t", table).param("c", column)
                .query(Boolean.class).single();
    }
}
