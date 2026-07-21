package com.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A fake checkout endpoint that produces realistic metric shapes: variable
 * latency, occasional errors, and a couple of custom business metrics on top
 * of the http.server.requests timer every Boot app gets for free.
 */
@RestController
public class CheckoutController {

    private final Counter ordersPlaced;
    private final Counter ordersFailed;
    private final Timer paymentTimer;
    private final Random random = new Random();

    public CheckoutController(MeterRegistry registry) {
        this.ordersPlaced = Counter.builder("shop.orders.placed")
                .description("Successfully placed orders")
                .register(registry);
        this.ordersFailed = Counter.builder("shop.orders.failed")
                .description("Orders that failed payment")
                .register(registry);
        this.paymentTimer = Timer.builder("shop.payment.duration")
                .description("Time spent in the (fake) payment provider")
                .publishPercentileHistogram()
                .register(registry);
    }

    @PostMapping("/checkout")
    Map<String, String> checkout() {
        return paymentTimer.record(() -> {
            simulateWork();
            if (random.nextInt(100) < 5) {
                ordersFailed.increment();
                throw new IllegalStateException("payment declined");
            }
            ordersPlaced.increment();
            return Map.of("status", "ok");
        });
    }

    @GetMapping("/products")
    Map<String, Object> products() {
        simulateWork();
        return Map.of("products", new String[]{"widget", "gizmo", "doohickey"});
    }

    private void simulateWork() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 120));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
