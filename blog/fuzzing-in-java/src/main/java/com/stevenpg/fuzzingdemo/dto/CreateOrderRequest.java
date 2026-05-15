package com.stevenpg.fuzzingdemo.dto;

import jakarta.validation.constraints.*;

/**
 * Request body for POST /api/orders.
 *
 * Both {@code quantity} and {@code unitPrice} are plain {@code int} fields.
 * The order controller multiplies them together using int arithmetic, which can
 * silently overflow when both values are large — that is the planted bug.
 * The fix (cast one operand to long before multiplying) is shown in the comment
 * inside OrderController#createOrder.
 */
public record CreateOrderRequest(

        @NotBlank(message = "productId is required")
        String productId,

        // Validation enforces a minimum of 1, but the maximum is left wide
        // deliberately so that values large enough to overflow int multiplication
        // still pass constraint validation.
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = Integer.MAX_VALUE)
        int quantity,

        // Unit price in whole cents. Same reasoning — deliberately no upper bound.
        @Min(value = 1, message = "unitPrice must be at least 1 cent")
        int unitPrice,

        @NotBlank(message = "shippingAddress is required")
        @Size(min = 5, max = 300)
        String shippingAddress,

        @Size(max = 500)
        String notes
) {}
