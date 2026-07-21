package com.stevenpg.gateway.webflux.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The landing spot for the CircuitBreaker filter. When the breaker on the orders
 * route is open (or a call exceeds the 1s time limiter — e.g. {@code /orders/slow}),
 * the gateway does an internal {@code forward:} to this handler instead of failing
 * the caller with a raw error.
 *
 * <p>This is where you put graceful degradation: a cached response, an empty list,
 * or — as here — a clear 503 that tells the client the upstream is protected.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "orders",
                        "message", "Orders is unavailable right now — served by the gateway fallback.",
                        "cause", "circuit breaker open or upstream call exceeded the 1s time limit",
                        "retryable", true));
    }
}
