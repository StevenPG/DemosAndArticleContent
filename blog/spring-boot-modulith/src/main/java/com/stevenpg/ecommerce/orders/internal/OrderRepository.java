package com.stevenpg.ecommerce.orders.internal;

import com.stevenpg.ecommerce.orders.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {}
