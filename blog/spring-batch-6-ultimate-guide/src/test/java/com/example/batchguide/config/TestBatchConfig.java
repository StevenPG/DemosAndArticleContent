package com.example.batchguide.config;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test-specific supplemental Spring configuration for the Order Import batch tests.
 *
 * <p>The primary test datasource (H2 in PostgreSQL-compatibility mode) is fully
 * auto-configured by Spring Boot from {@code application-test.yml}.  Spring Batch
 * meta-data tables are initialised by {@code spring.batch.initialize-schema: always}
 * in that profile.  Application tables ({@code orders}, {@code skipped_orders}) are
 * created by JPA's {@code ddl-auto: create-drop}.
 *
 * <p>This class is intentionally sparse: all infrastructure beans needed by the tests
 * ({@code DataSource}, {@code JdbcTemplate}, {@code JobRepository}, etc.) are
 * provided by Spring Boot auto-configuration and do not need to be re-declared here.
 *
 * <p>If a test needs to override a specific bean for isolation purposes, add a
 * {@code @Bean} method here and import this class with
 * {@code @Import(TestBatchConfig.class)} on the test class.
 *
 * <p>Currently used as a marker class so that future test-specific beans have a
 * natural home without scattering {@code @TestConfiguration} across multiple files.
 */
@TestConfiguration
public class TestBatchConfig {
    // No additional beans required — auto-configuration covers all infrastructure.
}
