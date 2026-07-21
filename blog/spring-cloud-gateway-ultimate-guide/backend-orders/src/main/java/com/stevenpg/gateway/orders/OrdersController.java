package com.stevenpg.gateway.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The orders backend. It is a completely ordinary Spring MVC controller — it has
 * no idea a gateway sits in front of it. Every endpoint here exists to make one
 * gateway feature <em>visible</em> from a curl on the other side of the gateway:
 *
 * <ul>
 *   <li>{@code GET /orders}          — plain routing (does traffic reach me at all?)</li>
 *   <li>{@code GET /orders/{id}}     — path variables survive rewriting</li>
 *   <li>{@code GET /orders/echo}     — reflects request headers so you can SEE the
 *                                      headers a gateway filter added/removed, the
 *                                      identity the gateway asserted, and the trace id</li>
 *   <li>{@code GET /orders/flaky}    — fails 2 out of every 3 calls, so a Retry
 *                                      filter visibly rescues the request</li>
 *   <li>{@code GET /orders/slow}     — sleeps, so a CircuitBreaker time limiter trips
 *                                      and the gateway serves its fallback</li>
 * </ul>
 */
@RestController
public class OrdersController {

    /**
     * Which process answered. When you run one backend it is always the same, but
     * echoing it keeps the response shape identical to the inventory service and
     * makes it obvious in logs which port served the call.
     */
    private final String instance;

    /** Deterministic flakiness: fail unless the call count is divisible by 3. */
    private final AtomicLong flakyCounter = new AtomicLong();

    public OrdersController(@Value("${server.port}") String port) {
        this.instance = "orders:" + port;
    }

    private static final List<Order> ORDERS = List.of(
            new Order("o-1001", "SKU-COFFEE", 2, "SHIPPED"),
            new Order("o-1002", "SKU-MUG", 1, "PENDING"),
            new Order("o-1003", "SKU-FILTER", 4, "DELIVERED")
    );

    @GetMapping("/orders")
    public Map<String, Object> list() {
        return Map.of("instance", instance, "orders", ORDERS);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> byId(@PathVariable String id) {
        return ORDERS.stream()
                .filter(o -> o.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Reflects the headers the backend actually received. This is the single most
     * useful endpoint for understanding a gateway: whatever the gateway's filters
     * did to the request shows up here. Look for:
     * <ul>
     *   <li>{@code X-Gateway} / {@code X-Request-Start} — headers a filter added</li>
     *   <li>{@code X-Auth-Subject} — identity the gateway extracted from the JWT and
     *       forwarded, so the backend never has to validate a token itself</li>
     *   <li>{@code traceparent} / {@code b3} — the distributed-trace context, proving
     *       the gateway and backend share one trace</li>
     * </ul>
     */
    @GetMapping("/orders/echo")
    public Map<String, Object> echo(@RequestHeader Map<String, String> headers) {
        return Map.of(
                "instance", instance,
                "message", "these are the headers I received from the gateway",
                "receivedHeaders", headers
        );
    }

    /**
     * Fails two out of every three calls with 503. A gateway Retry filter configured
     * for a few attempts will almost always turn this into a 200 — without the client
     * ever seeing a failure. Hit it directly (bypassing the gateway) to watch it fail.
     */
    @GetMapping("/orders/flaky")
    public ResponseEntity<Map<String, Object>> flaky() {
        long n = flakyCounter.incrementAndGet();
        if (n % 3 != 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("instance", instance, "attempt", n, "status", "TRANSIENT_FAILURE"));
        }
        return ResponseEntity.ok(Map.of("instance", instance, "attempt", n, "status", "OK"));
    }

    /**
     * Sleeps before responding. Paired with a CircuitBreaker filter whose time limiter
     * is set below this duration, the gateway gives up and serves a fallback instead of
     * hanging the caller. Repeated hits trip the breaker open.
     */
    @GetMapping("/orders/slow")
    public Map<String, Object> slow(@RequestHeader(value = "X-Sleep-Millis", required = false) Long sleepMillis)
            throws InterruptedException {
        long millis = sleepMillis != null ? sleepMillis : 3000L;
        Thread.sleep(millis);
        return Map.of("instance", instance, "sleptMillis", millis, "status", "OK");
    }

    /** An order. A record keeps the demo focused on the gateway, not on persistence. */
    public record Order(String id, String sku, int quantity, String status) {}
}
