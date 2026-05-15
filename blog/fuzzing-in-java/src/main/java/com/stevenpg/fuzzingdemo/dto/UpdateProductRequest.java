package com.stevenpg.fuzzingdemo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request body for PUT /api/products/{id}.
 *
 * All fields are optional — only non-null values are applied. This is the standard
 * "partial update" (PATCH-style via PUT) pattern.
 */
public record UpdateProductRequest(

        @Size(min = 1, max = 200)
        String name,

        @DecimalMin(value = "0.01")
        @DecimalMax(value = "999999.99")
        BigDecimal price,

        @Size(max = 500)
        String description
) {}
