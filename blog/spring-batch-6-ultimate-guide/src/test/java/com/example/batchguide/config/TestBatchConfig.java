package com.example.batchguide.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Test-specific Spring configuration that provides a real PostgreSQL instance via
 * Testcontainers. Using an actual database avoids the SQL compatibility gaps between
 * H2 PostgreSQL-compatibility mode and real PostgreSQL (e.g. ON CONFLICT ... EXCLUDED).
 *
 * Import this class with @Import(TestBatchConfig.class) on any integration test that
 * needs batch infrastructure.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestBatchConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
