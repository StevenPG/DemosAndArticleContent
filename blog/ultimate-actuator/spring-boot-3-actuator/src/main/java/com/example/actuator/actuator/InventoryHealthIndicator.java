package com.example.actuator.actuator;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A second custom health indicator, this time extending {@link AbstractHealthIndicator}
 * which provides exception handling for free (any thrown exception becomes a
 * {@code DOWN} with the error details).
 *
 * <p>It also introduces a <em>custom</em> {@link Status} ({@code DEGRADED}) to show that
 * you are not limited to {@code UP}/{@code DOWN}. Remember to map custom statuses to an
 * HTTP code via {@code management.endpoint.health.status.http-mapping} if needed.
 */
@Component
public class InventoryHealthIndicator extends AbstractHealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED", "Running with reduced capacity");

    private final AtomicInteger availableStock = new AtomicInteger(120);

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        int stock = availableStock.get();
        builder.withDetail("availableStock", stock)
                .withDetail("warehouse", "EU-WEST-1");
        if (stock <= 0) {
            builder.down().withDetail("reason", "out of stock");
        } else if (stock < 25) {
            builder.status(DEGRADED).withDetail("reason", "stock running low");
        } else {
            builder.up();
        }
    }
}
