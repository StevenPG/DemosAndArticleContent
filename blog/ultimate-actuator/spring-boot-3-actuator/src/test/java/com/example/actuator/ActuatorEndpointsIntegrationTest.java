package com.example.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@AutoConfigureMockMvc
@Testcontainers
class ActuatorEndpointsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("actuator")
            .withUsername("actuator")
            .withPassword("actuator");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

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
