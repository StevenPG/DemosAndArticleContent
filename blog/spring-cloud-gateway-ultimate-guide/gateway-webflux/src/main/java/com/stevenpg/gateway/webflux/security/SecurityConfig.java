package com.stevenpg.gateway.webflux.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Edge security: the gateway acts as an OAuth2 <b>resource server</b>. It rejects
 * requests to protected routes unless they carry a valid Bearer JWT, so the
 * backends behind it can trust that anything reaching them was already
 * authenticated at the edge.
 *
 * <p>Route protection policy:
 * <ul>
 *   <li>{@code /orders/**} — authenticated (a valid JWT is required)</li>
 *   <li>{@code /inventory/**} — public (read-only catalog)</li>
 *   <li>{@code /actuator/health}, {@code /fallback/**}, {@code /dev/**} — public</li>
 * </ul>
 *
 * <p>The JWT is validated with a symmetric HS256 key so the whole demo runs with
 * no external identity provider. In production you would delete the custom
 * {@link ReactiveJwtDecoder} bean and instead set
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} to your IdP.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                // A gateway is not a browser app; there is no session/cookie to protect.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/fallback/**", "/dev/**").permitAll()
                        .pathMatchers("/inventory/**").permitAll()
                        .pathMatchers("/java/**").permitAll()
                        .pathMatchers("/orders/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * A symmetric-key JWT decoder. Because Spring Boot can't build an HMAC decoder
     * from properties alone, we build it here from the shared demo secret. Providing
     * this bean makes the resource-server auto-config use it.
     */
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(@Value("${demo.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
