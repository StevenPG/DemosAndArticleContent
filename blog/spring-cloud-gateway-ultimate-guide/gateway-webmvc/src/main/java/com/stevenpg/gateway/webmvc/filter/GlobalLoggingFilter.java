package com.stevenpg.gateway.webmvc.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The servlet-gateway counterpart to the reactive {@code GlobalLoggingFilter}. In
 * the servlet world "a filter that runs on every request" is literally a
 * {@link jakarta.servlet.Filter}; extending {@link OncePerRequestFilter} and
 * registering it as a bean is all it takes.
 *
 * <p>Ordered at highest precedence so it wraps the whole chain (Spring Security,
 * the gateway routing, the round trip to the backend) and its timing reflects the
 * full request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        response.setHeader("X-Gateway-Handled", "webmvc");
        log.debug("--> {} {}", request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("<-- {} {} {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
        }
    }
}
