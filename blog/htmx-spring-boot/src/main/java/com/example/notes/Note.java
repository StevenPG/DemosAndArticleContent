package com.example.notes;

import java.time.Instant;

public record Note(long id, String title, String body, Instant updatedAt) {

    Note withContent(String title, String body) {
        return new Note(id, title, body, Instant.now());
    }
}
