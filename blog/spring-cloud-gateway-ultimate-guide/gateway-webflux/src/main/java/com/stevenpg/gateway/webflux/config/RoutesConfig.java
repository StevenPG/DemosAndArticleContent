package com.stevenpg.gateway.webflux.config;

import com.stevenpg.gateway.webflux.filter.AddCorrelationIdGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * The PROGRAMMATIC alternative to declaring routes in YAML. Everything here could
 * live in {@code application.yml}, and vice-versa — the two styles are equivalent
 * and can coexist (they do, in this app).
 *
 * <p>When to reach for the Java DSL instead of YAML:
 * <ul>
 *   <li>routes whose shape depends on other beans or runtime values;</li>
 *   <li>reusing a filter instance you built in code (see the custom
 *       {@link AddCorrelationIdGatewayFilterFactory} wired in below);</li>
 *   <li>when you want compile-time checking and IDE navigation over YAML strings.</li>
 * </ul>
 *
 * <p>This single route exposes the orders backend under a different prefix
 * ({@code /java/orders/**}) and rewrites the path back to {@code /orders/**} before
 * proxying — a compact tour of predicates, RewritePath, header filters, and using
 * a custom filter factory from Java.
 */
@Configuration
public class RoutesConfig {

    @Bean
    public RouteLocator programmaticRoutes(RouteLocatorBuilder builder,
                                           AddCorrelationIdGatewayFilterFactory correlationId) {
        return builder.routes()
                .route("orders-java", r -> r
                        .path("/java/orders/**")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                // /java/orders/123  ->  /orders/123
                                .rewritePath("/java/orders/(?<segment>.*)", "/orders/${segment}")
                                .addRequestHeader("X-Gateway", "webflux")
                                .addResponseHeader("X-Routed-By", "programmatic-RouteLocator")
                                // Reuse our custom filter factory with its defaults.
                                .filter(correlationId.apply(new AddCorrelationIdGatewayFilterFactory.Config())))
                        .uri("http://localhost:8081"))
                .build();
    }
}
