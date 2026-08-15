package com.stevenpg.jackson3example.json;

import java.math.BigDecimal;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import com.stevenpg.jackson3example.model.money.Money;

/**
 * The deserializer half of the custom {@link Money} type.
 *
 * <p><b>Migration note:</b> base class {@code JsonDeserializer<T>} becomes
 * {@code tools.jackson.databind.ValueDeserializer<T>} (same "Value" rename
 * as {@code ValueSerializer}). {@code DeserializationContext} keeps its
 * name unchanged in Jackson 3 - only the serialization-side context class
 * was renamed ({@code SerializerProvider -> SerializationContext}).
 *
 * <p>Compare with jackson2-example's {@code MoneyDeserializer}: there,
 * {@code deserialize} declares {@code throws IOException} (checked). Here
 * it declares nothing - {@code tools.jackson.core.JacksonException} is
 * unchecked, so it's legal to omit the {@code throws} clause entirely.
 */
public class MoneyDeserializer extends ValueDeserializer<Money> {

    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctxt) {
        // Parse "12.99 USD" back into amount + currency.
        String raw = p.getValueAsString();
        int spaceIndex = raw.lastIndexOf(' ');
        BigDecimal amount = new BigDecimal(raw.substring(0, spaceIndex));
        String currency = raw.substring(spaceIndex + 1);
        return new Money(amount, currency);
    }
}
