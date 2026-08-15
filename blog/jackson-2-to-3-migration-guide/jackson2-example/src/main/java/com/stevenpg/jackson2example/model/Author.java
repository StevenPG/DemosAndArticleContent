package com.stevenpg.jackson2example.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A hand-written (non-record) model class with an explicit {@code @JsonCreator}
 * constructor.
 *
 * <p><b>Migration note:</b> {@code @JsonCreator} and {@code @JsonProperty} live
 * in {@code com.fasterxml.jackson.annotation} - the {@code jackson-annotations}
 * artifact. This is the one Jackson module that did NOT move to the
 * {@code tools.jackson} groupId/package in Jackson 3; it stays at
 * {@code com.fasterxml.jackson.core:jackson-annotations} with the SAME
 * package. Every annotation import in this class is byte-for-byte identical
 * in the jackson3-example sibling class - the easiest part of the migration.
 *
 * <p>The genuinely version-specific part of this class is the
 * {@link Optional} field below - see the comment on {@link #twitterHandle}.
 */
public final class Author {

    private final String name;

    /**
     * {@code Optional<String>} as a JSON-bound field.
     *
     * <p><b>Migration note:</b> in Jackson 2, (de)serializing {@code Optional}
     * requires the separate {@code jackson-datatype-jdk8} module to be on the
     * classpath AND registered on the {@link com.fasterxml.jackson.databind.ObjectMapper}
     * (see {@code ObjectMapperConfig}). Without it, Jackson treats
     * {@code Optional} like any other POJO and serializes
     * {@code {"present": true, "empty": false}} instead of unwrapping the
     * value - a classic Jackson 2 footgun.
     *
     * <p>In Jackson 3, JDK8 datatype support (Optional, OptionalInt, OptionalLong,
     * OptionalDouble) is merged directly into jackson-databind - no extra
     * dependency, no registration, it just works. See the jackson3-example
     * sibling class for the identical field with zero extra ceremony required.
     */
    private final Optional<String> twitterHandle;

    @JsonCreator
    public Author(
            @JsonProperty("name") String name,
            @JsonProperty("twitterHandle") Optional<String> twitterHandle) {
        this.name = name;
        this.twitterHandle = twitterHandle == null ? Optional.empty() : twitterHandle;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getTwitterHandle() {
        return twitterHandle;
    }
}
