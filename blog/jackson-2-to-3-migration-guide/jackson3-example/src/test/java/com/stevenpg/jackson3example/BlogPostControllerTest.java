package com.stevenpg.jackson3example;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.stevenpg.jackson3example.model.CommentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that the Jackson-3-specific setup in this project works -
 * mirrors jackson2-example's {@code BlogPostControllerTest} test for test,
 * so a green run on both sides is itself part of the migration guide's
 * evidence: same behavior, less code required to get there.
 *
 * <p><b>Unrelated-to-Jackson migration note:</b> this test uses
 * {@link RestTestClient} instead of jackson2-example's
 * {@code TestRestTemplate} - Spring Boot 4.1 (Spring Framework 7) removed
 * {@code TestRestTemplate} in favor of this WebTestClient-style fluent API.
 * Not a Jackson change, but you WILL hit it the moment you bump Boot
 * major versions, so it's called out here rather than silently worked
 * around.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlogPostControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void sampleEndpointSerializesEveryFeatureCorrectly() {
        String json = client().get().uri("/api/posts/sample").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        JsonNode node = objectMapper.readTree(json);

        // @JsonProperty / Instant as ISO-8601 string, not a numeric timestamp -
        // works with zero extra modules registered.
        assertThat(node.get("publishedAt").asString()).isEqualTo("2026-01-15T10:30:00Z");

        // Optional<String> unwrapped to a plain string, NOT {"present":true,...} -
        // built into jackson-databind, no Jdk8Module needed.
        assertThat(node.get("author").get("twitterHandle").asString()).isEqualTo("@stevenpg");

        // @JsonInclude(NON_NULL): editorNote was null, so the key is absent entirely.
        assertThat(node.has("editorNote")).isFalse();

        // Custom Money (de)serializer: rendered as a single compact string.
        assertThat(node.get("sponsorshipFee").asString()).isEqualTo("49.99 USD");
    }

    @Test
    void recordDtoRoundTripsThroughDataBinding() {
        CommentDto request = new CommentDto("reader42", "Great migration guide!");
        CommentDto response = client().post().uri("/api/comments/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CommentDto.class)
                .returnResult().getResponseBody();
        assertThat(response).isEqualTo(request);
    }

    @Test
    void streamingApiProducesValidJson() {
        String json = client().get().uri("/api/health/streamed").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("service").asString()).isEqualTo("jackson3-example");
        assertThat(node.get("healthy").asBoolean()).isTrue();
    }

    @Test
    void treeModelEndpointAddsComputedFields() {
        String json = client().get().uri("/api/posts/sample/enriched").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("enrichedBy").asString()).isEqualTo("TreeModelEnricher");
        assertThat(node.get("wordCountEstimate").asInt()).isGreaterThan(0);
    }

    @Test
    void unserializableTypeIsMappedToA500ByTheAdviceEvenThoughItsUnchecked() {
        String body = client().get().uri("/api/posts/broken/raw-json").exchange()
                .expectStatus().is5xxServerError()
                .returnResult(String.class).getResponseBody();
        assertThat(body).contains("InvalidDefinitionException");
    }
}
