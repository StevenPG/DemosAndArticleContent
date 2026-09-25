package com.stevenpg.opsmcp.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The Spring AI MCP starters expose an *unauthenticated* JSON-RPC endpoint by
 * default. That is fine on stdio and nowhere else. This is the smallest thing
 * that is not nothing: a bearer token on /mcp only, compared in constant time.
 *
 * If ops.mcp.api-key is blank a random key is generated and logged at startup,
 * the same way Spring Security handles its default password. For anything
 * shared, put this behind your real auth (Spring Security resource server +
 * the MCP authorization spec) instead.
 */
@Configuration
public class McpApiKeyFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyFilter.class);

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> mcpApiKey(@Value("${ops.mcp.api-key:}") String configured,
                                                          @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
        String key = configured.isBlank() ? generate() : configured;
        byte[] expected = ("Bearer " + key).getBytes(StandardCharsets.UTF_8);

        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                byte[] presented = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);
                if (!MessageDigest.isEqual(expected, presented)) {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    response.sendError(HttpStatus.UNAUTHORIZED.value());
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.addUrlPatterns(endpoint, endpoint + "/*");
        registration.setName("mcpApiKeyFilter");
        return registration;
    }

    private static String generate() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes);
        log.warn("\n\nUsing generated MCP API key: {}\nSet ops.mcp.api-key to choose your own.\n", key);
        return key;
    }
}
