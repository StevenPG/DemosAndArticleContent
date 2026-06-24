package com.example.actuator.config;

// Spring Boot 4: EndpointRequest moved to the security auto-configuration module,
// and HealthEndpoint moved to the new spring-boot-health module.
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the Actuator endpoints &mdash; one of the most important production
 * concerns. The key tool here is {@link EndpointRequest}, a request matcher that
 * understands the actuator base path ({@code management.endpoints.web.base-path})
 * so you never hard-code {@code /actuator/**}.
 *
 * <p>Policy applied below:
 * <ul>
 *     <li>{@code health} and {@code info} are public (handy for load balancers / k8s probes).</li>
 *     <li>Every other actuator endpoint requires the {@code ACTUATOR_ADMIN} role.</li>
 *     <li>The business API under {@code /api/**} is left open for the demo.</li>
 * </ul>
 *
 * <p>Demo credentials: {@code admin} / {@code admin}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // health + info are safe to expose unauthenticated
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        // everything else under the actuator base path needs the admin role
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("ACTUATOR_ADMIN")
                        // the demo business API is open
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(httpBasic -> {})
                // CSRF disabled only to keep the demo's POST/DELETE calls simple via curl.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsManager() {
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("admin")
                .roles("ACTUATOR_ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
