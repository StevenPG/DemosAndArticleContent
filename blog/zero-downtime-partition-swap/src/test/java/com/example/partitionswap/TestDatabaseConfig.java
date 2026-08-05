package com.example.partitionswap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a real PostgreSQL 16 instance via Testcontainers. Nothing less will do here:
 * the code under test leans on native partitioning, {@code DETACH PARTITION ...
 * CONCURRENTLY} (PostgreSQL 14+), {@code inhdetachpending}, and catalog queries — none
 * of which exist in H2's compatibility mode.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
