package com.stevenpg.opsmcp.demo;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A deliberately flaky endpoint so the ops tools have something to find:
 * a configurable share of requests fail with a logged exception from a
 * pretend downstream, and some are slow. Nothing to do with MCP itself.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final double failureRate;
    private final double slowRate;

    public OrderController(@Value("${demo.failure-rate:0.05}") double failureRate,
                           @Value("${demo.slow-rate:0.05}") double slowRate) {
        this.failureRate = failureRate;
        this.slowRate = slowRate;
    }

    @GetMapping("/{id}")
    public Map<String, Object> order(@PathVariable long id) throws InterruptedException {
        var random = ThreadLocalRandom.current();
        log.debug("Fetching order {} from inventory-service", id);
        if (id <= 0) {
            log.warn("Rejected order lookup with non-positive id {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
        }
        if (random.nextDouble() < failureRate) {
            var cause = new java.net.SocketTimeoutException("Read timed out after 2000ms");
            log.error("Order {} lookup failed: inventory-service did not respond", id,
                    new IllegalStateException("inventory-service unavailable", cause));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "inventory-service unavailable");
        }
        if (random.nextDouble() < slowRate) {
            log.debug("Order {} took the slow path (cache miss)", id);
            Thread.sleep(random.nextLong(400, 1200));
        }
        return Map.of("id", id, "status", "SHIPPED", "items", random.nextInt(1, 6));
    }
}
