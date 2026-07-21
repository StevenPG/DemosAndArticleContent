package com.stevenpg.gateway.webmvc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Servlet-flavored edge security — the exact same policy as the reactive gateway's
 * {@code SecurityConfig}, expressed with {@link HttpSecurity} and a servlet
 * {@link JwtDecoder} instead of the reactive equivalents.
 *
 * <p>This side-by-side is a good way to internalize how thin the difference is:
 * {@code ServerHttpSecurity} -> {@code HttpSecurity},
 * {@code ReactiveJwtDecoder} -> {@code JwtDecoder},
 * {@code authorizeExchange} -> {@code authorizeHttpRequests}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/fallback/**", "/dev/**").permitAll()
                        .requestMatchers("/inventory/**").permitAll()
                        .requestMatchers("/yaml/**").permitAll()
                        .requestMatchers("/orders/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Symmetric HS256 decoder — same self-contained approach as the reactive gateway. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${demo.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
