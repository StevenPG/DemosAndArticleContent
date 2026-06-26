package com.example.actuator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
// Testcontainers 2.x relocated the modular containers into per-database packages.
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies a representative slice of the Actuator surface actually responds.
 *
 * <p>A real Postgres is provided by Testcontainers so the {@code db} health
 * indicator and Flyway migrations run for real. Requires a Docker daemon.
 */
@SpringBootTest
@Testcontainers
class ActuatorEndpointsIntegrationTest {

    // Testcontainers 2.x: the modular PostgreSQLContainer is no longer generic.
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("actuator")
            .withUsername("actuator")
            .withPassword("actuator");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc(WebApplicationContext context) {
        // Spring Boot 4 dropped the auto-configuration that used to apply Spring
        // Security's MockMvc support, so @WithMockUser is only honored when the
        // springSecurity() configurer is applied explicitly. Without it the request
        // reaches the filter chain as an anonymous user and the secured actuator
        // endpoints return 401.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        // health is remapped to /actuator/healthz via management.endpoints.web.path-mapping
        mockMvc.perform(get("/actuator/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/healthz/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACTUATOR_ADMIN")
    void customFeatureFlagsEndpointResponds() throws Exception {
        mockMvc.perform(get("/actuator/featureflags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['beta-search']").value(true));
    }

    @Test
    @WithMockUser(roles = "ACTUATOR_ADMIN")
    void prometheusScrapeEndpointResponds() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void securedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
