package com.stevenpg.jackson2example.model;

/**
 * A plain enum - enum handling is unchanged between Jackson 2 and 3.
 * Included so the model reads naturally; not a migration talking point.
 */
public enum PostStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
