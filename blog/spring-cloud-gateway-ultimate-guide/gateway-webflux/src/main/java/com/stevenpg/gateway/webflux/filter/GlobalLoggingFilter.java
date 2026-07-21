package com.stevenpg.gateway.webflux.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A CUSTOM {@code GlobalFilter} — unlike a {@code GatewayFilterFactory}, a
 * GlobalFilter is applied to <b>every</b> route with no per-route configuration.
 * Perfect for cross-cutting concerns like access logging, timing, or stamping a
 * header on all traffic.
 *
 * <p>This one logs one line when a request enters and one when it leaves, with the
 * wall-clock time spent inside the gateway. Because it also implements
 * {@link Ordered} and returns a very high precedence, it wraps the entire filter
 * chain — the measured time therefore includes all other filters and the round
 * trip to the backend.
 */
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        var request = exchange.getRequest();
        log.debug("--> {} {}", request.getMethod(), request.getURI().getRawPath());

        // Stamp a header proving this global filter ran. Set it now; it survives
        // onto the proxied response unless a downstream header overwrites it.
        exchange.getResponse().getHeaders().set("X-Gateway-Handled", "webflux");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("<-- {} {} {} ({} ms)",
                    request.getMethod(),
                    request.getURI().getRawPath(),
                    exchange.getResponse().getStatusCode(),
                    millis);
        }));
    }

    /**
     * Runs before the built-in filters (which sit at higher, later order values)
     * so the timing brackets the whole request. {@code HIGHEST_PRECEDENCE} is the
     * smallest int, i.e. first in, last out.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
