package com.example.batchguide.domain;

import java.math.BigDecimal;

/**
 * Immutable value object representing a single row read from the raw CSV input file.
 *
 * <p>This record is the <em>input</em> type for the batch pipeline — it carries the
 * raw, unvalidated data exactly as it appears in the source file.  Validation and
 * enrichment happen in the processor before the data is mapped to the {@link Order}
 * JPA entity.
 *
 * <p>Field layout mirrors the CSV column order:
 * <pre>
 * id,customerId,productCode,amount,orderDate
 * ORD001,C001,PROD-A,99.99,2024-01-01
 * </pre>
 *
 * @param id          unique order identifier (e.g. "ORD001")
 * @param customerId  foreign key to the customer dimension (e.g. "C001")
 * @param productCode SKU or product reference code (e.g. "PROD-A")
 * @param amount      order monetary value; may be zero which signals a filtered record
 * @param orderDate   ISO-8601 date string as read from CSV (e.g. "2024-01-01")
 */
public record OrderRecord(
        String id,
        String customerId,
        String productCode,
        BigDecimal amount,
        String orderDate
) {}
