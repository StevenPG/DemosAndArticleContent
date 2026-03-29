package com.example.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class OrderItemDTO {
    @NotBlank
    private String productId;

    @Positive
    private int quantity;

    // Getters and setters omitted for brevity
}
