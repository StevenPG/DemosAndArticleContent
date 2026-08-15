package com.stevenpg.jackson3example.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A hand-written (non-record) model class with an explicit {@code @JsonCreator}
 * constructor.
 *
 * <p><b>Migration note:</b> {@code @JsonCreator} and {@code @JsonProperty}
 * STILL live in {@code com.fasterxml.jackson.annotation} here in the Jackson
 * 3 project too - jackson-annotations is the one module that stayed at its
 * original {@code com.fasterxml.jackson.core} groupId and package. Compare
 * these two import lines with jackson2-example's {@code Author.java}: they
 * are identical.
 *
 * <p>The genuinely version-specific part of this class is the
 * {@link Optional} field below - see the comment on {@link #twitterHandle}.
 */
public final class Author {

    private final String name;

    /**
     * {@code Optional<String>} as a JSON-bound field.
     *
     * <p><b>Migration note:</b> in Jackson 3, JDK8 datatype support
     * (Optional, OptionalInt, OptionalLong, OptionalDouble) is merged
     * directly into jackson-databind - no {@code jackson-datatype-jdk8}
     * dependency, no module registration. It just works, as you can see
     * from the fact that {@code ObjectMapperConfig} in this project
     * registers no such module. Compare with jackson2-example's
     * {@code Author.java}, where the identical field required an explicit
     * {@code Jdk8Module} registration to unwrap correctly instead of
     * serializing as {@code {"present":true,"empty":false}}.
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
