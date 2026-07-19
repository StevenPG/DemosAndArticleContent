package com.example.orders.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository: CRUD plus derived queries generated from the
 * method names — no SQL written by hand.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    long countByStatus(OrderStatus status);
}
