package com.stevenpg.gateway.webmvc.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Landing spot for the CircuitBreaker filter's {@code forward:/fallback/orders}.
 * Identical in spirit to the reactive gateway's fallback — graceful degradation
 * instead of a raw error when the orders upstream is failing or too slow.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/orders")
    public ResponseEntity<Map<String, Object>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "orders",
                        "message", "Orders is unavailable right now — served by the gateway fallback.",
                        "cause", "circuit breaker open or upstream call failed",
                        "retryable", true));
    }
}
