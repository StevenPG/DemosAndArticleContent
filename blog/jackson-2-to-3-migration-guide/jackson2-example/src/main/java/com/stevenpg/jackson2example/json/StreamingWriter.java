package com.stevenpg.jackson2example.json;

import java.io.IOException;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the low-level STREAMING API (no data-binding, no POJOs) -
 * useful for writing huge payloads without materializing an object graph.
 *
 * <p><b>Migration note:</b> {@code JsonFactory}/{@code JsonGenerator} live in
 * {@code com.fasterxml.jackson.core} in Jackson 2, moving to
 * {@code tools.jackson.core} in Jackson 3 - same class names, new package.
 * Two things also change on {@code JsonGenerator} itself:
 * {@code writeStringField}/{@code writeBooleanField}/{@code writeNumberField}
 * (used below) are renamed to {@code writeStringProperty}/
 * {@code writeBooleanProperty}/{@code writeNumberProperty} in Jackson 3 -
 * "field" implied JSON objects specifically, "property" applies across
 * every backend format databind supports (CBOR, Smile, ...). And every
 * method here declares {@code throws IOException} (checked); the Jackson 3
 * equivalents declare {@code throws JacksonException} (UNCHECKED), so the
 * try/catch (or `throws` on the caller) becomes optional rather than
 * mandatory. See the jackson3-example sibling class for both changes
 * applied.
 */
@Component
public class StreamingWriter {

    private final JsonFactory jsonFactory = new JsonFactory();

    public String writeHealthPayload(String service, boolean healthy) throws IOException {
        StringWriter out = new StringWriter();

        // try-with-resources is required here because JsonGenerator.close()
        // also declares `throws IOException`.
        try (JsonGenerator generator = jsonFactory.createGenerator(out)) {
            generator.writeStartObject();
            generator.writeStringField("service", service);
            generator.writeBooleanField("healthy", healthy);
            generator.writeNumberField("checkedAtEpochSeconds", System.currentTimeMillis() / 1000);
            generator.writeEndObject();
        }

        return out.toString();
    }
}
