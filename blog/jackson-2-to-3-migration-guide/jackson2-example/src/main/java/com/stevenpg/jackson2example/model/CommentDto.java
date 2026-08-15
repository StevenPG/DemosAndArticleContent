package com.stevenpg.jackson2example.model;

/**
 * A Java {@code record} used directly as a JSON DTO.
 *
 * <p><b>Not a migration concern:</b> Jackson has supported records natively
 * since jackson-databind 2.12 (released 2020) - it reads the canonical
 * constructor's component names via reflection, no {@code @JsonCreator} and
 * no extra module required. That native support carries over unchanged into
 * Jackson 3, so this file is IDENTICAL in the jackson3-example sibling
 * project except for the (absent) import changes. Included here mainly to
 * reassure migrators: "my records will keep working."
 */
public record CommentDto(String author, String body) {
}
