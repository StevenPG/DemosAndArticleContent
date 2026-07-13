package com.example.orders.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Inbound security: the API is an OAuth2 resource server. Keycloak issues the
 * JWTs (issuer configured in application.yaml); Spring validates the signature
 * against the realm's JWKS and maps each entry in the token's {@code scope}
 * claim to a {@code SCOPE_*} authority used in the rules below.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Pure token-based API: no sessions, no CSRF token exchange.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Liveness/metrics stay open so compose healthchecks
                        // and Prometheus can scrape without a token.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/orders/**")
                        .hasAuthority("SCOPE_orders:read")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/orders/**")
                        .hasAuthority("SCOPE_orders:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
