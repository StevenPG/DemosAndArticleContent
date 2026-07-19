package com.example.orders.config;

import com.example.orders.domain.OrderRepository;
import com.example.orders.domain.OrderStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduled background job (enabled by @EnableScheduling on the application
 * class). Every 30 seconds it counts unfinished orders, logs the number, and
 * publishes it as a Micrometer gauge that shows up in /actuator/prometheus
 * as {@code orders_pending}.
 */
@Component
public class PendingOrdersReporter {

    private static final Logger log = LoggerFactory.getLogger(PendingOrdersReporter.class);

    private final OrderRepository repository;
    private final AtomicLong pendingCount = new AtomicLong();

    public PendingOrdersReporter(OrderRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        Gauge.builder("orders.pending", pendingCount, AtomicLong::doubleValue)
                .description("Orders not yet completed or failed")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.reporting.interval:30s}")
    public void reportPendingOrders() {
        long pending = repository.countByStatus(OrderStatus.PENDING)
                + repository.countByStatus(OrderStatus.PROCESSING);
        pendingCount.set(pending);
        log.info("Pending/processing orders: {}", pending);
    }
}
