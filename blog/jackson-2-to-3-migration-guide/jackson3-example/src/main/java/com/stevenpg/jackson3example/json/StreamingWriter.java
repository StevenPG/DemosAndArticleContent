package com.stevenpg.jackson3example.json;

import java.io.StringWriter;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;

import org.springframework.stereotype.Component;

/**
 * Demonstrates the low-level STREAMING API (no data-binding, no POJOs) -
 * identical purpose to jackson2-example's {@code StreamingWriter}.
 *
 * <p><b>Migration note:</b> {@code JsonFactory}/{@code JsonGenerator} move
 * from {@code com.fasterxml.jackson.core} to {@code tools.jackson.core}
 * (plus {@code JsonFactory} itself lives one sub-package deeper, under
 * {@code tools.jackson.core.json}). One method rename to know:
 * {@code writeStringField}/{@code writeBooleanField}/{@code writeNumberField}
 * (Jackson 2) became {@code writeStringProperty}/{@code writeBooleanProperty}/
 * {@code writeNumberProperty} (Jackson 3) - "field" implied JSON objects
 * specifically, while "property" applies across every backend format
 * (CBOR, Smile, ...) databind supports.
 *
 * <p>The behavioral difference: in jackson2-example, every method here
 * (including {@code close()}) declares {@code throws IOException} -
 * checked, forcing the try-with-resources block to also declare or handle
 * it. Here, the equivalent methods declare
 * {@code throws tools.jackson.core.JacksonException} - UNCHECKED - so the
 * method below needs no {@code throws} clause at all.
 */
@Component
public class StreamingWriter {

    private final JsonFactory jsonFactory = new JsonFactory();

    public String writeHealthPayload(String service, boolean healthy) {
        StringWriter out = new StringWriter();

        // try-with-resources is still good practice for releasing the
        // generator's internal buffers, but it's no longer REQUIRED by the
        // compiler the way it was for the checked IOException in Jackson 2.
        try (JsonGenerator generator = jsonFactory.createGenerator(out)) {
            generator.writeStartObject();
            generator.writeStringProperty("service", service);
            generator.writeBooleanProperty("healthy", healthy);
            generator.writeNumberProperty("checkedAtEpochSeconds", System.currentTimeMillis() / 1000);
            generator.writeEndObject();
        }

        return out.toString();
    }
}
