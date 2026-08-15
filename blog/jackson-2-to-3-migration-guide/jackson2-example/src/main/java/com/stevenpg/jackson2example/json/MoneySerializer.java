package com.stevenpg.jackson2example.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.stevenpg.jackson2example.model.money.Money;

/**
 * A custom serializer: Jackson 2's base class is {@code JsonSerializer<T>},
 * and {@link #serialize} takes a {@code SerializerProvider}.
 *
 * <p><b>Migration note (rename):</b> in Jackson 3 this class becomes
 * {@code tools.jackson.databind.ValueSerializer<T>} - renamed because
 * Jackson 3's databind layer is no longer JSON-only in spirit (the class
 * hierarchy is shared with non-JSON backends like CBOR/Smile), so "Json" in
 * the name was misleading. The third constructor parameter also becomes
 * {@code SerializationContext} (renamed from {@code SerializerProvider}).
 *
 * <p><b>Migration note (checked vs unchecked):</b> this method signature
 * declares {@code throws IOException} - a CHECKED exception the caller must
 * handle. In Jackson 3, {@code ValueSerializer.serialize} declares
 * {@code throws JacksonException}, which is UNCHECKED (extends
 * {@code RuntimeException}) - nothing to catch or declare. See the
 * jackson3-example sibling class for the identical logic with one line
 * removed.
 */
public class MoneySerializer extends JsonSerializer<Money> {

    @Override
    public void serialize(Money value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // Render as a single compact string: "12.99 USD"
        gen.writeString(value.getAmount().toPlainString() + " " + value.getCurrency());
    }
}
