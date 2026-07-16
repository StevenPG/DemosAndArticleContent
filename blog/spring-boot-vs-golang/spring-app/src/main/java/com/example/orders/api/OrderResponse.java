package com.example.orders.api;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerEmail,
        String item,
        int quantity,
        long totalCents,
        OrderStatus status,
        String paymentId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getItem(),
                order.getQuantity(),
                order.getTotalCents(),
                order.getStatus(),
                order.getPaymentId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
