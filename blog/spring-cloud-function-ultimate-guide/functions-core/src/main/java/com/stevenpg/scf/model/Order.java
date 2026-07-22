package com.stevenpg.scf.model;

import java.math.BigDecimal;

/**
 * The raw order that enters the pipeline.
 *
 * <p>A plain record — Spring Cloud Function will convert JSON (or any other
 * registered content type) into this POJO for us before invoking a function,
 * and back to JSON on the way out. That conversion is the same no matter which
 * surface (HTTP, Kafka, RSocket, Lambda) the order arrives on.
 */
public record Order(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        int itemCount) {
}
