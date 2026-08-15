package com.stevenpg.jackson2example.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevenpg.jackson2example.model.money.Money;

/**
 * The core domain object every endpoint in this project reads or writes.
 *
 * <p>Deliberately exercises three annotation-driven behaviors that are
 * IDENTICAL in Jackson 3 (because {@code jackson-annotations} did not move):
 *
 * <ul>
 *   <li>{@code @JsonProperty} - explicit wire name, decoupled from the Java
 *       field name</li>
 *   <li>{@code @JsonInclude(NON_NULL)} - omit {@code editorNote} from the
 *       JSON entirely when it is null, instead of writing {@code "editorNote":null}</li>
 *   <li>{@code @JsonCreator} - tells Jackson which constructor to use for
 *       deserialization</li>
 * </ul>
 *
 * <p>The genuinely Jackson-2-specific part of this class is the
 * {@link Instant} field - see the comment on {@link #publishedAt}.
 */
public final class BlogPost {

    private final String title;
    private final Author author;
    private final PostStatus status;

    /**
     * A {@code java.time.Instant} field.
     *
     * <p><b>Migration note:</b> in Jackson 2, java.time support comes from
     * the separate {@code jackson-datatype-jsr310} module, and you must
     * register {@code new JavaTimeModule()} on the {@code ObjectMapper}
     * yourself (see {@code ObjectMapperConfig}) - miss that step and Jackson
     * either throws {@code InvalidDefinitionException} (no serializer found)
     * or falls back to serializing the Instant's private fields as a raw
     * object, depending on configuration.
     *
     * <p>In Jackson 3, java.time support is merged directly into
     * jackson-databind (package {@code tools.jackson.databind.ext.javatime}) -
     * no separate module, no registration. See the jackson3-example sibling
     * class for the identical field working with zero extra setup.
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
