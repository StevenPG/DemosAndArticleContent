package com.stevenpg.ecommerce.payments;

import com.stevenpg.ecommerce.orders.OrderManagement;
import com.stevenpg.ecommerce.payments.internal.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository repository;
    private final OrderManagement orderManagement;
    private final ApplicationEventPublisher events;

    PaymentService(PaymentRepository repository, OrderManagement orderManagement, ApplicationEventPublisher events) {
        this.repository = repository;
        this.orderManagement = orderManagement;
        this.events = events;
    }

    public Payment process(UUID orderId, BigDecimal amount) {
        var payment = new Payment(orderId, amount);

        // Simulated gateway: amounts ending in .99 are always declined (demo behaviour)
        boolean approved = !amount.toPlainString().endsWith(".99");

        if (approved) {
            payment.complete();
            repository.save(payment);
            orderManagement.confirm(orderId);
            events.publishEvent(new PaymentCompletedEvent(orderId, payment.getId()));
        } else {
            payment.fail();
            repository.save(payment);
            orderManagement.markPaymentFailed(orderId);
            events.publishEvent(new PaymentFailedEvent(orderId, "Gateway declined"));
        }

        return payment;
    }
}
