package com.example.notes;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store so the demo has zero infrastructure. The article is about
 * the HTMX interaction model, not persistence.
 */
@Repository
public class NoteStore {

    private final Map<Long, Note> notes = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public NoteStore() {
        create("Welcome to HTMX Notes",
                "Every interaction on this page is a server round trip returning an HTML fragment. Open devtools and watch the network tab.");
        create("No build step",
                "There is no npm, no bundler, and no JSON API. The only JavaScript is htmx itself, served as a webjar.");
    }

    public List<Note> findAll(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return notes.values().stream()
                .filter(n -> q.isBlank()
                        || n.title().toLowerCase(Locale.ROOT).contains(q)
                        || n.body().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Note::updatedAt).reversed())
                .toList();
    }

    public Note find(long id) {
        Note note = notes.get(id);
        if (note == null) {
            throw new IllegalArgumentException("No note with id " + id);
        }
        return note;
    }

    public Note create(String title, String body) {
        long id = ids.incrementAndGet();
        Note note = new Note(id, title, body, Instant.now());
        notes.put(id, note);
        return note;
    }

    public Note update(long id, String title, String body) {
        Note updated = find(id).withContent(title, body);
        notes.put(id, updated);
        return updated;
    }

    public void delete(long id) {
        notes.remove(id);
    }
}
