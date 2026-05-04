package com.example.batchguide.writer;

import com.example.batchguide.domain.Order;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring Batch item writer configuration for the order import pipeline.
 *
 * <p>Provides a {@link JdbcBatchItemWriter} that persists enriched {@link Order}
 * objects to the {@code orders} table using an upsert strategy:
 * <ul>
 *   <li>New orders are inserted with all columns populated.</li>
 *   <li>Existing orders (matched on primary key {@code id}) have their
 *       {@code customer_name} and {@code amount} updated — the {@code created_at}
 *       timestamp is intentionally left unchanged on conflict so we can tell when
 *       an order first arrived.</li>
 * </ul>
 *
 * <p>The JDBC batch writer sends all items in a chunk as a single prepared-statement
 * batch, which is significantly more efficient than individual INSERT calls for large
 * datasets.
 *
 * <p>{@code BeanPropertyItemSqlParameterSourceProvider} is used to bind {@link Order}
 * getter values to named SQL parameters (e.g. {@code :customerId} → {@code getCustomerId()}).
 * This requires {@link Order} to have standard JavaBean getters, which Lombok's
 * {@code @Data} provides.
 */
@Configuration
public class OrderItemWriter {

    /**
     * Upsert SQL for the {@code orders} table.
     *
     * <p>The {@code ON CONFLICT (id) DO UPDATE} clause handles idempotent reruns —
     * running the same CSV file twice will not duplicate rows.  H2 in PostgreSQL
     * compatibility mode also supports this syntax, so the same SQL works in tests.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO orders (id, customer_id, customer_name, product_code, amount, order_date, created_at)
            VALUES (:id, :customerId, :customerName, :productCode, :amount, :orderDate, NOW())
            ON CONFLICT (id) DO UPDATE
                SET customer_name = EXCLUDED.customer_name,
                    amount        = EXCLUDED.amount
            """;

    /**
     * Creates the {@link JdbcBatchItemWriter} for {@link Order} entities.
     *
     * <p>The writer is <em>not</em> {@code @StepScope} because it holds no per-step
     * state — it is safe to share the same writer instance across steps and threads.
     *
     * @param dataSource the primary application datasource autowired by Spring Boot
     * @return a configured {@link JdbcBatchItemWriter} ready for chunk-oriented writes
     */
    @Bean
    public JdbcBatchItemWriter<Order> jdbcOrderWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Order>()
                .dataSource(dataSource)
                .sql(UPSERT_SQL)
                // BeanPropertyItemSqlParameterSourceProvider maps Order getters to
                // named parameters (:id → getId(), :customerId → getCustomerId(), etc.)
                .beanMapped()
                // Assert that at least one row was affected; throws if the upsert
                // affected 0 rows, which would indicate a schema mismatch.
                .assertUpdates(false) // false because ON CONFLICT may produce 0 inserts
                .build();
    }
}
