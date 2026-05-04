package com.example.batchguide.processor;

import com.example.batchguide.domain.Order;
import com.example.batchguide.domain.OrderRecord;
import com.example.batchguide.exception.ValidationException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Spring Batch item processor for the order import pipeline.
 *
 * <p>Responsibilities (in order):
 * <ol>
 *   <li><strong>Validate</strong> — throws {@link ValidationException} if any
 *       mandatory field ({@code id}, {@code customerId}, {@code productCode}) is
 *       blank or null.  The step is configured to skip up to 10 such exceptions.</li>
 *   <li><strong>Filter</strong> — returns {@code null} when {@code amount} is zero
 *       or null, instructing Spring Batch to discard the item without writing it.
 *       This counts towards the step's filter count, not the write count.</li>
 *   <li><strong>Enrich</strong> — resolves the customer name from an in-memory
 *       lookup map and maps the raw {@link OrderRecord} to the {@link Order} JPA
 *       entity ready for persistence.</li>
 * </ol>
 *
 * <p>{@code @StepScope} ensures a new processor instance per step execution, which
 * is required when the processor holds per-execution state (it doesn't here, but the
 * annotation is included to demonstrate the pattern and to allow future addition of
 * step-scoped job parameters if needed).
 *
 * <p>Example unit-test usage:
 * <pre>{@code
 * OrderItemProcessor processor = new OrderItemProcessor();
 * Order order = processor.process(new OrderRecord("ORD001","C001","PROD-A",
 *                                                  new BigDecimal("99.99"), "2024-01-01"));
 * assertEquals("Alice", order.getCustomerName());
 * }</pre>
 */
@Component
@StepScope
public class OrderItemProcessor implements ItemProcessor<OrderRecord, Order> {

    /**
     * Static in-memory customer registry used for enrichment.
     *
     * <p>In a production system this would typically be loaded from a database or
     * external service.  For this guide it is hard-coded to keep the example
     * self-contained and to avoid an extra network call per record.
     *
     * <p>Key: customerId (matches the CSV column).  Value: customer display name.
     */
    private static final Map<String, String> CUSTOMER_NAMES = Map.of(
            "C001", "Alice",
            "C002", "Bob",
            "C003", "Carol",
            "C004", "Dave",
            "C005", "Eve"
    );

    /**
     * Validates, filters, and enriches a single {@link OrderRecord}.
     *
     * @param item the raw record read from the CSV; never {@code null} (Spring Batch
     *             guarantees non-null items to the processor)
     * @return an enriched {@link Order} entity ready for persistence, or {@code null}
     *         if the item should be silently filtered out (amount is zero)
     * @throws ValidationException if {@code id}, {@code customerId}, or
     *                             {@code productCode} is blank or null — the step
     *                             will skip the item and invoke the
     *                             {@link com.example.batchguide.listener.OrderSkipListener}
     */
    @Override
    public Order process(OrderRecord item) throws ValidationException {

        // ---- Validation phase ----
        // All three fields are mandatory; a missing value indicates a data-quality
        // issue that should be captured in the skipped_orders audit table.
        if (item.id() == null || item.id().isBlank()) {
            throw new ValidationException("Order id is blank: " + item);
        }
        if (item.customerId() == null || item.customerId().isBlank()) {
            throw new ValidationException(
                    "customerId is blank for order: " + item.id());
        }
        if (item.productCode() == null || item.productCode().isBlank()) {
            throw new ValidationException(
                    "productCode is blank for order: " + item.id());
        }

        // ---- Filter phase ----
        // Zero-amount orders are considered no-ops and are silently discarded.
        // Returning null tells Spring Batch to increment the filter count and not
        // pass the item to the writer.
        if (item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        // ---- Enrichment phase ----
        // Resolve the customer name; fall back to "Unknown" if the id is not in the
        // registry (e.g. for new customers added after the lookup was last refreshed).
        String customerName = CUSTOMER_NAMES.getOrDefault(item.customerId(), "Unknown");

        return Order.builder()
                .id(item.id())
                .customerId(item.customerId())
                .customerName(customerName)
                .productCode(item.productCode())
                .amount(item.amount())
                .orderDate(item.orderDate())
                // createdAt is set by the database via NOW() in the upsert SQL;
                // leaving it null here so the DB-side default takes effect.
                .createdAt(null)
                .build();
    }
}
