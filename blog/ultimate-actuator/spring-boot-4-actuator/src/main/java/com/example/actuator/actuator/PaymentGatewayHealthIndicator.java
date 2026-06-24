package com.example.actuator.actuator;

// Spring Boot 4: the health contributor API moved out of the actuator module
// into the dedicated `spring-boot-health` module, under this new package.
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * A custom {@link HealthIndicator}. Its bean name (minus the {@code HealthIndicator}
 * suffix) becomes the key in the {@code health} endpoint &mdash; here {@code paymentGateway}.
 *
 * <p>It reports {@code UP} with rich {@code details}, simulating a check against an
 * external payment provider. Because it is a regular bean, it can be slotted into a
 * health <em>group</em> (see {@code application.yml}) such as {@code readiness}.
 */
@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final Instant startedAt = Instant.now();

    @Override
    public Health health() {
        // In a real app you'd ping the provider here. We simulate a healthy response.
        long latencyMs = 42;
        if (latencyMs > 1000) {
            return Health.down()
                    .withDetail("provider", "AcmePay")
                    .withDetail("latencyMs", latencyMs)
                    .withDetail("reason", "latency above threshold")
                    .build();
        }
        return Health.up()
                .withDetail("provider", "AcmePay")
                .withDetail("latencyMs", latencyMs)
                .withDetail("uptime", Duration.between(startedAt, Instant.now()).toString())
                .build();
    }
}
