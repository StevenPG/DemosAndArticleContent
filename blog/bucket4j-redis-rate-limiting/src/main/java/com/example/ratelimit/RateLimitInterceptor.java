package com.example.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Two-tier limiting:
 *  - callers with an X-Api-Key header get a generous per-key limit (100 req/min
 *    with a burst of 20)
 *  - anonymous callers share a strict per-IP limit (20 req/min)
 *
 * Bucket state lives in Redis, so limits hold across all app instances.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final LettuceBasedProxyManager<byte[]> proxyManager;

    public RateLimitInterceptor(LettuceBasedProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String apiKey = request.getHeader("X-Api-Key");
        String bucketKey;
        BucketConfiguration config;

        if (apiKey != null && !apiKey.isBlank()) {
            bucketKey = "rl:key:" + apiKey;
            config = apiKeyLimit();
        } else {
            bucketKey = "rl:ip:" + clientIp(request);
            config = anonymousLimit();
        }

        BucketProxy bucket = proxyManager.builder()
                .build(bucketKey.getBytes(StandardCharsets.UTF_8), () -> config);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error": "rate limit exceeded", "retryAfterSeconds": %d}
                """.formatted(retryAfterSeconds));
        return false;
    }

    /** 100 requests/minute, refilled gradually, burst capacity 120. */
    private BucketConfiguration apiKeyLimit() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(120)
                        .refillGreedy(100, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** 20 requests/minute for anonymous traffic. */
    private BucketConfiguration anonymousLimit() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(20)
                        .refillGreedy(20, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
