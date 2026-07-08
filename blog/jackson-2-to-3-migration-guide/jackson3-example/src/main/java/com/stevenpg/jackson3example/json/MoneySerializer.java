package com.stevenpg.jackson3example.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import com.stevenpg.jackson3example.model.money.Money;

/**
 * A custom serializer: Jackson 3's base class is
 * {@code tools.jackson.databind.ValueSerializer<T>} - renamed from Jackson
 * 2's {@code JsonSerializer<T>} because databind's serialization hierarchy
 * is no longer JSON-only in spirit (shared with CBOR/Smile/etc backends),
 * so the "Json" prefix was misleading.
 *
 * <p>The third parameter is {@code SerializationContext}, renamed from
 * Jackson 2's {@code SerializerProvider}.
 *
 * <p><b>Migration note (checked vs unchecked):</b> compare this signature
 * with jackson2-example's {@code MoneySerializer}: there, {@code serialize}
 * declares {@code throws IOException} (checked). Here it declares
 * {@code throws tools.jackson.core.JacksonException}, which extends
 * {@code RuntimeException} - UNCHECKED. Nothing here needs a {@code throws}
 * clause or a try/catch at all; it's shown for symmetry with the Jackson 2
 * signature, not because it's required.
 */
public class MoneySerializer extends ValueSerializer<Money> {

    @Override
    public void serialize(Money value, JsonGenerator gen, SerializationContext context) {
        // Render as a single compact string: "12.99 USD"
        gen.writeString(value.getAmount().toPlainString() + " " + value.getCurrency());
    }
}
