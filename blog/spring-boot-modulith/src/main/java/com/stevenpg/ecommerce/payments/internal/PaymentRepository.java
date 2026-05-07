package com.stevenpg.ecommerce.payments.internal;

import com.stevenpg.ecommerce.payments.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {}
