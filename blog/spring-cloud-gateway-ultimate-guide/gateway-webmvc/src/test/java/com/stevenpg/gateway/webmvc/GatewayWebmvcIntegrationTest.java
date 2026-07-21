package com.stevenpg.gateway.webmvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration test for the SERVLET gateway, wired to a REAL Redis via
 * Testcontainers (needs Docker). Deliberately a near-verbatim mirror of the reactive
 * gateway's test — the only real difference is {@link RestTestClient} (Spring's
 * servlet test client) in place of WebTestClient — which drives home how little
 * changes between the two flavors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class GatewayWebmvcIntegrationTest {

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

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void protectedRouteRejectsAnonymousCallers() {
        client.get().uri("/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void allRoutesAreRegistered() {
        // Spring Cloud Gateway Server WebMVC doesn't expose /actuator/gateway/routes
        // (that's reactive-only as of 5.0.2), so route registration is asserted
        // behaviorally instead: a registered route with no live backend fails
        // downstream (500), while a genuinely unmatched path 404s from Spring itself.
        client.get().uri("/inventory/whoami")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(404));

        client.get().uri("/this-path-matches-no-route")
                .exchange()
                .expectStatus().isNotFound();
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

        // Valid token clears the edge; without a backend it fails downstream, but NOT 401/403.
        client.get().uri("/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }
}
