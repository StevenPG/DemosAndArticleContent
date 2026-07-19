package com.example.orders.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/orders. Bean Validation annotations are enforced
 * automatically by @Valid on the controller parameter; violations surface as
 * a 400 ProblemDetail via the exception handler.
 */
public record CreateOrderRequest(
        @NotBlank @Email String customerEmail,
        @NotBlank String item,
        @Min(1) @Max(1000) int quantity,
        @Positive long totalCents) {
}
