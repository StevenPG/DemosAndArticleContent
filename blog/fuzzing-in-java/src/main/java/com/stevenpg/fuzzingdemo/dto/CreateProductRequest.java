package com.stevenpg.fuzzingdemo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body for POST /api/products.
 *
 * The {@code metadata} map intentionally permits null values for keys — the product
 * controller accesses those values without null-checking, which is the planted bug
 * for the fuzzer to discover (see ProductController#createProduct).
 */
public record CreateProductRequest(

        @NotBlank(message = "product name is required")
        @Size(min = 1, max = 200)
        String name,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than zero")
        @DecimalMax(value = "999999.99", message = "price exceeds maximum allowed value")
        BigDecimal price,

        @Size(max = 500)
        String description,

        // Optional discount code; must be alphanumeric when present.
        @Pattern(regexp = "^[A-Z0-9]{4,12}$", message = "discount code must be 4-12 uppercase alphanumeric characters")
        String discountCode,

        // Percentage discount (0-100). Must be provided together with discountCode.
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        BigDecimal discountPercent,

        // Arbitrary key-value metadata. Values may be null — bug lives here.
        Map<String, String> metadata
) {}
