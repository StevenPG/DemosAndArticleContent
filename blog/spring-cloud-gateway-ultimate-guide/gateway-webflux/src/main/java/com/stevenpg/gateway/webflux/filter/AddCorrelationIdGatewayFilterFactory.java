package com.stevenpg.gateway.webflux.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.UUID;

/**
 * A CUSTOM {@code GatewayFilterFactory} — the reactive way to write your own
 * per-route filter that takes arguments.
 *
 * <p>Spring Cloud Gateway derives the filter's name from the class name by
 * stripping the {@code GatewayFilterFactory} suffix, so this class is referenced
 * as <b>{@code AddCorrelationId}</b> in {@code application.yml} and as
 * {@code .filter(correlationId.apply(...))} in the programmatic RouteLocator.
 *
 * <p>What it does: guarantees every proxied request carries a correlation id.
 * If the client already sent one (in {@code X-Correlation-Id} by default) it is
 * preserved; otherwise a UUID is generated. The same id is echoed back on the
 * response so a caller can tie its logs to the gateway's and the backend's.
 *
 * <p>This is deliberately distinct from distributed tracing: tracing ids are for
 * your observability backend, a correlation id is a business/customer-facing
 * handle you might print in an error page or a support ticket.
 */
@Component
public class AddCorrelationIdGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AddCorrelationIdGatewayFilterFactory.Config> {

    public AddCorrelationIdGatewayFilterFactory() {
        super(Config.class);
    }

    /**
     * Lets the filter be configured with the shorthand form
     * {@code AddCorrelationId=X-My-Header} in YAML (positional arg -> headerName).
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("headerName");
    }

    @Override
    public GatewayFilter apply(Config config) {
        String header = config.getHeaderName();
        return (exchange, chain) -> {
            String existing = exchange.getRequest().getHeaders().getFirst(header);
            String correlationId = (existing != null && !existing.isBlank())
                    ? existing
                    : UUID.randomUUID().toString();

            // Mutate the REQUEST so the backend receives the header...
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.headers(h -> h.set(header, correlationId)))
                    .build();

            // ...and set it on the RESPONSE so the caller gets it back.
            mutated.getResponse().getHeaders().set(header, correlationId);

            return chain.filter(mutated);
        };
    }

    /** Per-route configuration for this filter. */
    public static class Config {
        private String headerName = "X-Correlation-Id";

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }
}
