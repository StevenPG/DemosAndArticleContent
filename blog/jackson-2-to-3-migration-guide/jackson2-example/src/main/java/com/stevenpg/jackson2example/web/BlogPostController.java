package com.stevenpg.jackson2example.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stevenpg.jackson2example.json.StreamingWriter;
import com.stevenpg.jackson2example.json.TreeModelEnricher;
import com.stevenpg.jackson2example.model.Author;
import com.stevenpg.jackson2example.model.BlogPost;
import com.stevenpg.jackson2example.model.CommentDto;
import com.stevenpg.jackson2example.model.PostStatus;
import com.stevenpg.jackson2example.model.Unserializable;
import com.stevenpg.jackson2example.model.money.Money;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One controller, one endpoint per Jackson feature this project showcases.
 * Every endpoint here has a byte-for-byte-comparable twin in
 * jackson3-example's {@code BlogPostController} - read them side by side.
 */
@RestController
@RequestMapping("/api")
public class BlogPostController {

    private final ObjectMapper objectMapper;
    private final StreamingWriter streamingWriter;
    private final TreeModelEnricher treeModelEnricher;

    public BlogPostController(
            ObjectMapper objectMapper,
            StreamingWriter streamingWriter,
            TreeModelEnricher treeModelEnricher) {
        this.objectMapper = objectMapper;
        this.streamingWriter = streamingWriter;
        this.treeModelEnricher = treeModelEnricher;
    }

    /**
     * Plain data-binding: Spring MVC serializes the returned {@link BlogPost}
     * using our custom {@code ObjectMapper} bean automatically. Exercises
     * annotations, {@code Instant}, {@code Optional}, and the custom
     * {@code Money} type all in one payload.
     */
    @GetMapping("/posts/sample")
    public BlogPost sample() {
        return new BlogPost(
                "Jackson 2 to 3, a Field Guide",
                new Author("Steven Gantz", Optional.of("@stevenpg")),
                PostStatus.PUBLISHED,
                Instant.parse("2026-01-15T10:30:00Z"),
                List.of("jackson", "migration", "spring-boot"),
                null, // editorNote - omitted from JSON via @JsonInclude(NON_NULL)
                new Money(new BigDecimal("49.99"), "USD"));
    }

    /** A record DTO round-tripped through data-binding - see {@link CommentDto}. */
    @PostMapping("/comments/echo")
    public CommentDto echoComment(@RequestBody CommentDto comment) {
        return comment;
    }

    /**
     * Manually invokes {@link ObjectMapper#writeValueAsString} and declares
     * {@code throws JsonProcessingException}.
     *
     * <p><b>Migration note:</b> {@code JsonProcessingException} is CHECKED in
     * Jackson 2 (it extends {@code JacksonException}, which extends
     * {@code IOException}) - the compiler forces this method to either catch
     * it or declare it, and Spring MVC's exception resolution machinery has
     * to know about it too (see {@code JacksonErrorAdvice}). In Jackson 3 the
     * equivalent call site declares nothing extra - {@code JacksonException}
     * there is UNCHECKED. See jackson3-example's identical endpoint with the
     * {@code throws} clause removed entirely.
     */
    @GetMapping("/posts/sample/raw-json")
    public String sampleAsRawJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(sample());
    }

    /** Low-level streaming API - see {@link StreamingWriter}. */
    @GetMapping("/health/streamed")
    public String streamedHealth() throws IOException {
        return streamingWriter.writeHealthPayload("jackson2-example", true);
    }

    /** Tree-model API - see {@link TreeModelEnricher}. */
    @GetMapping("/posts/sample/enriched")
    public JsonNode enrichedSample() {
        return treeModelEnricher.enrich(sample());
    }

    /**
     * Deliberately fails: {@link Unserializable} has no discoverable
     * properties, so both Jackson versions refuse to serialize it by
     * default. This is where the checked-vs-unchecked distinction actually
     * bites at runtime - see {@code JacksonErrorAdvice}.
     */
    @GetMapping("/posts/broken/raw-json")
    public String brokenAsRawJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(new Unserializable());
    }
}
