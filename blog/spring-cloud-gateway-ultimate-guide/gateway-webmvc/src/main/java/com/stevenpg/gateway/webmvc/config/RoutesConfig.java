package com.stevenpg.gateway.webmvc.config;

import com.stevenpg.gateway.webmvc.ratelimit.RateLimitConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.method;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * The servlet gateway's routes, defined with the FUNCTIONAL API.
 *
 * <p>This is the headline contrast with the reactive gateway: instead of a YAML
 * {@code routes:} list (or a {@code RouteLocator}), each route is a
 * {@code RouterFunction<ServerResponse>} bean built from static helpers:
 * <ul>
 *   <li>{@code route(id).route(predicate, http())} — match and proxy;</li>
 *   <li>{@code .before(uri(...))} — set the target (or {@code lb(...)} for load balancing);</li>
 *   <li>{@code .before(...)} / {@code .after(...)} — request/response filters;</li>
 *   <li>{@code .filter(circuitBreaker(...))} / {@code .filter(retry(...))} / {@code .filter(rateLimit(...))}.</li>
 * </ul>
 *
 * <p>Each route is a separate bean so its filters stay scoped to it; {@code @Order}
 * decides precedence when two routes could match the same path (flaky before the
 * catch-all orders route).
 */
@Configuration
public class RoutesConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ATTR = RoutesConfig.class.getName() + ".cid";
    private static final String SUBJECT_HEADER = "X-Auth-Subject";

    /** Where the orders backend lives. Overridable (e.g. by tests pointing at a mock). */
    @Value("${backend.orders-uri:http://localhost:8081}")
    private String ordersUri;

    // -----------------------------------------------------------------------
    // 1) RETRY — /orders/flaky fails 2/3 of the time; retry rescues it.
    // -----------------------------------------------------------------------
    @Bean
    @Order(0)
    public RouterFunction<ServerResponse> ordersFlakyRoute() {
        return route("orders-flaky")
                .route(path("/orders/flaky").and(method(HttpMethod.GET)), http())
                .before(uri(ordersUri))
                .filter(retry(3))
                .build();
    }

    // -----------------------------------------------------------------------
    // 2) The main ORDERS route: circuit breaker + Bucket4j rate limit + custom
    //    correlation-id filter + identity propagation + header filters. Secured.
    // -----------------------------------------------------------------------
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> ordersRoute() {
        return route("orders")
                .route(path("/orders/**"), http())
                .before(uri(ordersUri))
                .before(propagateIdentity())                 // strip-then-assert X-Auth-Subject
                .before(ensureCorrelationId())               // custom: generate/keep correlation id
                .before(addRequestHeader("X-Gateway", "webmvc"))
                .before(removeRequestHeader("Cookie"))
                .filter(circuitBreaker("ordersCb", URI.create("forward:/fallback/orders")))
                .filter(rateLimit(c -> c
                        .setCapacity(10)                     // burst
                        .setPeriod(Duration.ofSeconds(2))    // refill 10 tokens / 2s  => 5/s
                        .setKeyResolver(RateLimitConfig::resolveKey)))
                .after(writeCorrelationId())                 // custom: echo correlation id on response
                .after(addResponseHeader("X-Gateway-Flavor", "webmvc"))
                .build();
    }

    // -----------------------------------------------------------------------
    // 3) INVENTORY route — client-side LOAD BALANCING across the two instances
    //    registered under `backend-inventory` in application.yml. Public.
    // -----------------------------------------------------------------------
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> inventoryRoute() {
        return route("inventory")
                .route(path("/inventory/**").and(method(HttpMethod.GET)), http())
                .filter(lb("backend-inventory"))             // resolves lb -> a concrete instance
                .after(addResponseHeader("X-Load-Balanced", "true"))
                .build();
    }

    // ======================= custom filters =================================

    /**
     * CUSTOM before/after pair implementing the same correlation-id behavior as the
     * reactive gateway's custom GatewayFilterFactory: keep the caller's id or mint
     * one, forward it to the backend, and echo it on the response. The generated id
     * is stashed in a request attribute so the after-filter can read it back.
     */
    private static Function<ServerRequest, ServerRequest> ensureCorrelationId() {
        return request -> {
            String existing = request.headers().firstHeader(CORRELATION_HEADER);
            String correlationId = StringUtils.hasText(existing) ? existing : UUID.randomUUID().toString();
            request.attributes().put(CORRELATION_ATTR, correlationId);
            return ServerRequest.from(request).header(CORRELATION_HEADER, correlationId).build();
        };
    }

    private static BiFunction<ServerRequest, ServerResponse, ServerResponse> writeCorrelationId() {
        return (request, response) -> {
            Object correlationId = request.attributes().get(CORRELATION_ATTR);
            if (correlationId != null) {
                response.headers().add(CORRELATION_HEADER, correlationId.toString());
            }
            return response;
        };
    }

    /**
     * Strip any client-supplied {@code X-Auth-Subject} (anti-spoofing), then assert
     * the real authenticated subject from the servlet SecurityContext. Mirror of the
     * reactive gateway's IdentityPropagationGlobalFilter.
     */
    private static Function<ServerRequest, ServerRequest> propagateIdentity() {
        return request -> {
            ServerRequest.Builder builder = ServerRequest.from(request)
                    .headers(headers -> headers.remove(SUBJECT_HEADER));
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                builder.header(SUBJECT_HEADER, auth.getName());
            }
            return builder.build();
        };
    }
}
