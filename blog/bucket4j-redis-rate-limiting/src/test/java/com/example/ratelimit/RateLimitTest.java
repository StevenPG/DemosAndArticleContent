package com.example.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:8").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisUri(DynamicPropertyRegistry registry) {
        registry.add("rate-limit.redis-uri",
                () -> "redis://%s:%d".formatted(redis.getHost(), redis.getMappedPort(6379)));
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void anonymousCallersGet429AfterTwentyRequests() {
        int okCount = 0;
        int limitedCount = 0;
        for (int i = 0; i < 25; i++) {
            ResponseEntity<String> response = rest.getForEntity("/api/quote", String.class);
            if (response.getStatusCode() == HttpStatus.OK) okCount++;
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) limitedCount++;
        }
        assertThat(okCount).isEqualTo(20);
        assertThat(limitedCount).isEqualTo(5);
    }

    @Test
    void apiKeyCallersGetTheirOwnLargerBucket() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", "test-key");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = rest.exchange(
                "/api/quote", HttpMethod.GET, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Rate-Limit-Remaining"))
                .isEqualTo("119");
    }
}
