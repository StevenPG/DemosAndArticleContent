package com.stevenpg.jackson3example.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevenpg.jackson3example.model.money.Money;

/**
 * The core domain object every endpoint in this project reads or writes -
 * mirrors jackson2-example's {@code BlogPost} field for field.
 *
 * <p>The three annotation-driven behaviors below ({@code @JsonProperty},
 * {@code @JsonInclude(NON_NULL)}, {@code @JsonCreator}) use the SAME
 * {@code com.fasterxml.jackson.annotation} imports as the Jackson 2 project -
 * annotations did not move packages in Jackson 3.
 *
 * <p>The genuinely Jackson-3-specific part of this class is the
 * {@link Instant} field - see the comment on {@link #publishedAt}.
 */
public final class BlogPost {

    private final String title;
    private final Author author;
    private final PostStatus status;

    /**
     * A {@code java.time.Instant} field.
     *
     * <p><b>Migration note:</b> in Jackson 3, java.time support (Instant,
     * LocalDate, LocalDateTime, OffsetDateTime, ...) is merged directly into
     * jackson-databind, in package {@code tools.jackson.databind.ext.javatime} -
     * no separate {@code jackson-datatype-jsr310} dependency, no
     * {@code JavaTimeModule} registration. Compare with jackson2-example's
     * {@code BlogPost.java}, where the identical field requires both.
     */
    private final Instant publishedAt;

    private final List<String> tags;

    /** Demonstrates {@code @JsonInclude(NON_NULL)}: omitted from JSON when null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String editorNote;

    /**
     * A custom value type with no built-in Jackson support - exercises the
     * {@code MoneyModule} registered in {@code ObjectMapperConfig}. Null
     * when a post has no sponsorship, and omitted from JSON in that case.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Money sponsorshipFee;

    @JsonCreator
    public BlogPost(
            @JsonProperty("title") String title,
            @JsonProperty("author") Author author,
            @JsonProperty("status") PostStatus status,
            @JsonProperty("publishedAt") Instant publishedAt,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("editorNote") String editorNote,
            @JsonProperty("sponsorshipFee") Money sponsorshipFee) {
        this.title = title;
        this.author = author;
        this.status = status;
        this.publishedAt = publishedAt;
        this.tags = tags;
        this.editorNote = editorNote;
        this.sponsorshipFee = sponsorshipFee;
    }

    public String getTitle() {
        return title;
    }

    public Author getAuthor() {
        return author;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getEditorNote() {
        return editorNote;
    }

    public Money getSponsorshipFee() {
        return sponsorshipFee;
    }
}
