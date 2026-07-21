package com.stevenpg.gateway.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REACTIVE gateway.
 *
 * <p>Everything interesting is either in {@code application.yml} (declarative
 * routes) or in the small set of {@code @Component}/{@code @Configuration} classes
 * in this package:
 * <ul>
 *   <li>{@link com.stevenpg.gateway.webflux.filter.GlobalLoggingFilter} — a GlobalFilter</li>
 *   <li>{@link com.stevenpg.gateway.webflux.filter.AddCorrelationIdGatewayFilterFactory} — a custom filter factory</li>
 *   <li>{@link com.stevenpg.gateway.webflux.security.SecurityConfig} — edge JWT validation</li>
 *   <li>{@link com.stevenpg.gateway.webflux.security.IdentityPropagationGlobalFilter} — forwards identity downstream</li>
 *   <li>{@link com.stevenpg.gateway.webflux.ratelimit.RateLimiterConfig} — the rate-limiter KeyResolver</li>
 *   <li>{@link com.stevenpg.gateway.webflux.config.RoutesConfig} — a programmatic RouteLocator</li>
 * </ul>
 */
@SpringBootApplication
public class GatewayWebfluxApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayWebfluxApplication.class, args);
    }
}
