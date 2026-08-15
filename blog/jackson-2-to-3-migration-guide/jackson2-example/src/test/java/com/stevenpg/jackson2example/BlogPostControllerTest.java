package com.stevenpg.jackson2example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stevenpg.jackson2example.model.CommentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that the Jackson-2-specific setup in this project
 * (explicit module registration, custom Money type, Optional/Instant
 * handling, checked-exception advice) all actually works together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlogPostControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void sampleEndpointSerializesEveryFeatureCorrectly() throws Exception {
        String json = rest.getForObject(url("/api/posts/sample"), String.class);
        JsonNode node = objectMapper.readTree(json);

        // @JsonProperty / Instant as ISO-8601 string, not a numeric timestamp.
        assertThat(node.get("publishedAt").asText()).isEqualTo("2026-01-15T10:30:00Z");

        // Optional<String> unwrapped to a plain string, NOT {"present":true,...} -
        // proof Jdk8Module is registered.
        assertThat(node.get("author").get("twitterHandle").asText()).isEqualTo("@stevenpg");

        // @JsonInclude(NON_NULL): editorNote was null, so the key is absent entirely.
        assertThat(node.has("editorNote")).isFalse();

        // Custom Money (de)serializer: rendered as a single compact string.
        assertThat(node.get("sponsorshipFee").asText()).isEqualTo("49.99 USD");
    }

    @Test
    void recordDtoRoundTripsThroughDataBinding() {
        CommentDto request = new CommentDto("reader42", "Great migration guide!");
        CommentDto response = rest.postForObject(url("/api/comments/echo"), request, CommentDto.class);
        assertThat(response).isEqualTo(request);
    }

    @Test
    void streamingApiProducesValidJson() throws Exception {
        String json = rest.getForObject(url("/api/health/streamed"), String.class);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("service").asText()).isEqualTo("jackson2-example");
        assertThat(node.get("healthy").asBoolean()).isTrue();
    }

    @Test
    void treeModelEndpointAddsComputedFields() throws Exception {
        String json = rest.getForObject(url("/api/posts/sample/enriched"), String.class);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("enrichedBy").asText()).isEqualTo("TreeModelEnricher");
        assertThat(node.get("wordCountEstimate").asInt()).isGreaterThan(0);
    }

    @Test
    void unserializableTypeIsMappedToA500ByTheCheckedExceptionAdvice() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/posts/broken/raw-json"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("InvalidDefinitionException");
    }
}
