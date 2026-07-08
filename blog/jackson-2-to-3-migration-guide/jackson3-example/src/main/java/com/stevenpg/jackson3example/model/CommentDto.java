package com.stevenpg.jackson3example.model;

/**
 * A Java {@code record} used directly as a JSON DTO.
 *
 * <p><b>Not a migration concern:</b> record support has been native in
 * Jackson since 2.12 and carries over unchanged into Jackson 3 - Jackson
 * reads the canonical constructor's component names via reflection, no
 * extra module, no {@code @JsonCreator}. This file is identical to
 * jackson2-example's {@code CommentDto.java} apart from the package name.
 */
public record CommentDto(String author, String body) {
}
