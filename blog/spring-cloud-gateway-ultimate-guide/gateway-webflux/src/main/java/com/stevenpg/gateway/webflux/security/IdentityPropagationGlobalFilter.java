package com.stevenpg.gateway.webflux.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * Turns "the edge validated a JWT" into something the backends can use without
 * doing any crypto themselves: a plain {@code X-Auth-Subject} header carrying the
 * authenticated subject.
 *
 * <p>Two responsibilities, both security-critical:
 * <ol>
 *   <li><b>Strip</b> any client-supplied {@code X-Auth-Subject} first. Otherwise a
 *       caller could forge the header and impersonate anyone — the classic
 *       "confused deputy" against a gateway. The gateway is the only thing allowed
 *       to set this header.</li>
 *   <li><b>Assert</b> the real subject (from the validated token) when the request
 *       is authenticated.</li>
 * </ol>
 *
 * <p>Public routes (e.g. {@code /inventory/**}) have no principal, so the header is
 * simply absent — the stripping still runs, so a forged value never gets through.
 */
@Component
public class IdentityPropagationGlobalFilter implements GlobalFilter, Ordered {

    static final String SUBJECT_HEADER = "X-Auth-Subject";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Always remove any inbound value — never trust the client for identity.
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> h.remove(SUBJECT_HEADER)))
                .build();

        return stripped.getPrincipal()
                .filter(p -> p instanceof Authentication auth && auth.isAuthenticated())
                .map(Principal::getName)
                .map(subject -> stripped.mutate()
                        .request(r -> r.headers(h -> h.set(SUBJECT_HEADER, subject)))
                        .build())
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    /**
     * Runs after the logging filter but still well before the routing/proxy filter,
     * so the header is in place before the request leaves for the backend.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
