package com.example.batchguide.config;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Spring Batch 6 infrastructure configuration.
 *
 * <p>Extends {@link JdbcDefaultBatchConfiguration} — the Spring Batch 6 extension
 * point for JDBC-backed batch repositories. Overriding {@link #getDataSource()} and
 * {@link #getTransactionManager()} wires the {@link org.springframework.batch.core.repository.JobRepository},
 * {@link org.springframework.batch.core.launch.JobLauncher}, and related beans
 * to the application's primary datasource.
 *
 * <p>{@code @EnableBatchProcessing} is intentionally absent — in Spring Batch 6 it
 * is a no-op; {@link JdbcDefaultBatchConfiguration} replaces it entirely.
 *
 * <p>The {@code batchTransactionManager} bean is also exposed so step builders in
 * {@link com.example.batchguide.job.OrderImportJob} can qualify-inject it alongside
 * the JPA transaction manager registered by Spring Data.
 */
@Configuration
public class BatchConfig extends JdbcDefaultBatchConfiguration {

    @Autowired
    private DataSource dataSource;

    /**
     * JDBC-based transaction manager used by all chunk steps and tasklets.
     *
     * <p>Exposed as a named bean so step builders can {@code @Qualifier}-inject it
     * and avoid ambiguity with the {@code JpaTransactionManager} registered by
     * Spring Data JPA.
     */
    @Bean
    public PlatformTransactionManager batchTransactionManager() {
        return new JdbcTransactionManager(dataSource);
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Returns the same {@code batchTransactionManager} singleton for the batch
     * infrastructure. Calling the {@code @Bean} method goes through the CGLIB proxy
     * so only one instance is ever created.
     */
    @Override
    protected PlatformTransactionManager getTransactionManager() {
        return batchTransactionManager();
    }
}
