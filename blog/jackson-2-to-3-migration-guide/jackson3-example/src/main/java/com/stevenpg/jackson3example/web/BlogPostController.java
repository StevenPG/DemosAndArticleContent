package com.stevenpg.jackson3example.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.stevenpg.jackson3example.json.StreamingWriter;
import com.stevenpg.jackson3example.json.TreeModelEnricher;
import com.stevenpg.jackson3example.model.Author;
import com.stevenpg.jackson3example.model.BlogPost;
import com.stevenpg.jackson3example.model.CommentDto;
import com.stevenpg.jackson3example.model.PostStatus;
import com.stevenpg.jackson3example.model.Unserializable;
import com.stevenpg.jackson3example.model.money.Money;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One controller, one endpoint per Jackson feature this project showcases -
 * mirrors jackson2-example's {@code BlogPostController} endpoint for
 * endpoint. Read them side by side.
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
     * {@code Money} type all in one payload - zero extra modules needed for
     * the first two, versus two explicit ones in jackson2-example.
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
     * Manually invokes {@link ObjectMapper#writeValueAsString}.
     *
     * <p><b>Migration note:</b> compare this method with jackson2-example's
     * identical endpoint, which must declare
     * {@code throws JsonProcessingException} because that exception is
     * CHECKED there. Here, {@code writeValueAsString} declares
     * {@code throws tools.jackson.core.JacksonException} - UNCHECKED - so
     * this method needs no {@code throws} clause at all. The method body is
     * otherwise identical.
     */
    @GetMapping("/posts/sample/raw-json")
    public String sampleAsRawJson() {
        return objectMapper.writeValueAsString(sample());
    }

    /** Low-level streaming API - see {@link StreamingWriter}. */
    @GetMapping("/health/streamed")
    public String streamedHealth() {
        return streamingWriter.writeHealthPayload("jackson3-example", true);
    }

    /** Tree-model API - see {@link TreeModelEnricher}. */
    @GetMapping("/posts/sample/enriched")
    public JsonNode enrichedSample() {
        return treeModelEnricher.enrich(sample());
    }

    /**
     * Deliberately fails: {@link Unserializable} has no discoverable
     * properties, so Jackson 3 refuses to serialize it by default, same as
     * Jackson 2. The difference is entirely in whether the compiler forces
     * this method to say so - see {@code JacksonErrorAdvice}.
     */
    @GetMapping("/posts/broken/raw-json")
    public String brokenAsRawJson() {
        return objectMapper.writeValueAsString(new Unserializable());
    }
}
