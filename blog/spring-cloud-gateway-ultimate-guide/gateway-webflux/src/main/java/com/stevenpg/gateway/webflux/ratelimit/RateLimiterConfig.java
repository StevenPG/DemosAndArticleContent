package com.stevenpg.gateway.webflux.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * The {@code KeyResolver} decides <b>what</b> the RequestRateLimiter counts. The
 * Redis token bucket is maintained per key, so choosing the key is choosing the
 * fairness policy.
 *
 * <p>This resolver prefers the authenticated subject (so each user gets their own
 * bucket) and falls back to the client IP for anonymous traffic. It is referenced
 * from {@code application.yml} as {@code key-resolver: "#{@userKeyResolver}"}.
 *
 * <p>Try it: hammer {@code /orders} faster than replenishRate (5/s) and you'll
 * start getting HTTP 429 with a {@code X-RateLimit-Remaining: 0} header.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var remote = exchange.getRequest().getRemoteAddress();
                    return (remote != null && remote.getAddress() != null)
                            ? remote.getAddress().getHostAddress()
                            : "anonymous";
                }));
    }
}
