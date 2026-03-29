package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class OrderDTO {
    @NotNull
    @Size(min = 1)
    private List<@Valid OrderItemDTO> items;

    @Pattern(regexp = "^[0-9]{16}$")
    private String cardNumber;

    @Future
    private java.time.LocalDate deliveryDate;

    // Getters and setters omitted for brevity
}
