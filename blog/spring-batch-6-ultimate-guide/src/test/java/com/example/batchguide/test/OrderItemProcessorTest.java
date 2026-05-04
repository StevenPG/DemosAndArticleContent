package com.example.batchguide.test;

import com.example.batchguide.domain.Order;
import com.example.batchguide.domain.OrderRecord;
import com.example.batchguide.exception.ValidationException;
import com.example.batchguide.processor.OrderItemProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Plain unit tests for {@link OrderItemProcessor}.
 *
 * <p>No Spring application context is loaded — the processor is instantiated directly.
 * This makes the tests fast and avoids any infrastructure dependencies.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Happy-path: valid record produces a correctly mapped {@link Order}</li>
 *   <li>Missing {@code id}: {@link ValidationException} is thrown</li>
 *   <li>Zero amount: processor returns {@code null} (filter)</li>
 *   <li>Missing {@code customerId}: {@link ValidationException} is thrown</li>
 *   <li>Missing {@code productCode}: {@link ValidationException} is thrown</li>
 * </ul>
 */
class OrderItemProcessorTest {

    private OrderItemProcessor processor;

    @BeforeEach
    void setUp() {
        // Direct instantiation — no Spring wiring needed for this unit test
        processor = new OrderItemProcessor();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid record is mapped to enriched Order with customer name")
    void validRecord_returnsMappedOrder() throws Exception {
        OrderRecord record = new OrderRecord(
                "ORD001", "C001", "PROD-A", new BigDecimal("99.99"), "2024-01-01");

        Order result = processor.process(record);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("ORD001");
        assertThat(result.getCustomerId()).isEqualTo("C001");
        // Verify enrichment: customer name should be resolved from the in-memory map
        assertThat(result.getCustomerName()).isEqualTo("Alice");
        assertThat(result.getProductCode()).isEqualTo("PROD-A");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(result.getOrderDate()).isEqualTo("2024-01-01");
    }

    @Test
    @DisplayName("Unknown customerId resolves to 'Unknown' customer name")
    void unknownCustomerId_resolvesToUnknown() throws Exception {
        OrderRecord record = new OrderRecord(
                "ORD999", "C999", "PROD-X", new BigDecimal("50.00"), "2024-06-01");

        Order result = processor.process(record);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("Unknown");
    }

    // -------------------------------------------------------------------------
    // Validation failures
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Missing id throws ValidationException")
    void missingId_throwsValidationException() {
        OrderRecord record = new OrderRecord(
                "", "C001", "PROD-A", new BigDecimal("99.99"), "2024-01-01");

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("Null id throws ValidationException")
    void nullId_throwsValidationException() {
        OrderRecord record = new OrderRecord(
                null, "C001", "PROD-A", new BigDecimal("99.99"), "2024-01-01");

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Missing customerId throws ValidationException")
    void missingCustomerId_throwsValidationException() {
        OrderRecord record = new OrderRecord(
                "ORD011", "", "PROD-F", new BigDecimal("39.99"), "2024-01-11");

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("Missing productCode throws ValidationException")
    void missingProductCode_throwsValidationException() {
        OrderRecord record = new OrderRecord(
                "ORD012", "C001", "", new BigDecimal("29.99"), "2024-01-12");

        assertThatThrownBy(() -> processor.process(record))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("productCode");
    }

    // -------------------------------------------------------------------------
    // Filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Zero amount returns null (filter — item discarded, not written)")
    void zeroAmount_returnsNull() throws Exception {
        OrderRecord record = new OrderRecord(
                "ORD013", "C002", "PROD-G", BigDecimal.ZERO, "2024-01-13");

        Order result = processor.process(record);

        // null instructs Spring Batch to discard the item and increment filter count
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Null amount returns null (filter)")
    void nullAmount_returnsNull() throws Exception {
        OrderRecord record = new OrderRecord(
                "ORD014", "C003", "PROD-H", null, "2024-01-14");

        Order result = processor.process(record);

        assertThat(result).isNull();
    }
}
