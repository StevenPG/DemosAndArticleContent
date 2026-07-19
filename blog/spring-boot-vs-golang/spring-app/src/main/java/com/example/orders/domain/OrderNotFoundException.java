package com.example.orders.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Order %s not found".formatted(id));
    }
}
