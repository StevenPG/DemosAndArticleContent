package com.stevenpg.gateway.webflux;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration test for the reactive gateway, wired to a REAL Redis via
 * Testcontainers (needs a running Docker daemon). It proves the whole thing boots
 * and the security + routing + dev-token machinery works end to end:
 * <ul>
 *   <li>protected routes reject anonymous callers (401);</li>
 *   <li>every declared route is registered and visible on the actuator endpoint;</li>
 *   <li>a JWT minted by the dev endpoint actually passes the resource-server check.</li>
 * </ul>
 *
 * <p>Rate limiting, load balancing and the resilience filters are exercised live by
 * {@code scripts/demo-requests.sh}; here we focus on what can be asserted without a
 * live backend so the test stays deterministic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class GatewayWebfluxIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    WebTestClient.Builder builder;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = builder.baseUrl("http://localhost:" + port).build();
    }

    @Test
    void protectedRouteRejectsAnonymousCallers() {
        client.get().uri("/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void allRoutesAreRegistered() {
        client.get().uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.route_id=='orders')]").exists()
                .jsonPath("$[?(@.route_id=='orders-flaky')]").exists()
                .jsonPath("$[?(@.route_id=='inventory')]").exists();
    }

    @Test
    void mintedTokenPassesResourceServerValidation() {
        Map<?, ?> tokenResponse = client.get().uri("/dev/token?sub=tester")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(tokenResponse).isNotNull();
        String token = (String) tokenResponse.get("access_token");
        assertThat(token).isNotBlank();

        // With a valid token the request clears the edge (no longer 401). There is no
        // backend in this test, so it will fail downstream — but NOT with 401/403.
        client.get().uri("/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }
}
