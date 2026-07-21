package com.example.ratelimit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class ApiController {

    @GetMapping("/api/quote")
    Map<String, Object> quote() {
        return Map.of(
                "quote", "The best rate limiter is the one your users never notice.",
                "timestamp", Instant.now().toString());
    }
}
