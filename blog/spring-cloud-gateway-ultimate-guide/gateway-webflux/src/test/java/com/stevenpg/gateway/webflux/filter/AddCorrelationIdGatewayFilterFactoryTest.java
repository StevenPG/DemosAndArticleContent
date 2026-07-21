package com.stevenpg.gateway.webflux.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the custom correlation-id filter — no Spring context, no
 * Redis, no network. Proves the two behaviors that matter: mint an id when the
 * caller didn't send one, and preserve the caller's id when they did. Either way
 * the same value ends up on both the forwarded request and the response.
 */
class AddCorrelationIdGatewayFilterFactoryTest {

    private final AddCorrelationIdGatewayFilterFactory factory = new AddCorrelationIdGatewayFilterFactory();
    private static final String HEADER = "X-Correlation-Id";

    @Test
    void generatesCorrelationIdWhenAbsent() {
        GatewayFilter filter = factory.apply(new AddCorrelationIdGatewayFilterFactory.Config());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String sentDownstream = forwarded.get().getRequest().getHeaders().getFirst(HEADER);
        assertThat(sentDownstream).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst(HEADER)).isEqualTo(sentDownstream);
    }

    @Test
    void preservesCorrelationIdWhenPresent() {
        GatewayFilter filter = factory.apply(new AddCorrelationIdGatewayFilterFactory.Config());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(HEADER, "caller-supplied-123"));

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HEADER)).isEqualTo("caller-supplied-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(HEADER)).isEqualTo("caller-supplied-123");
    }
}
