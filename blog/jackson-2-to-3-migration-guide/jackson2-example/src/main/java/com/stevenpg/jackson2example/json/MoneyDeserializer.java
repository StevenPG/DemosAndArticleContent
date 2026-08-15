package com.stevenpg.jackson2example.json;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.stevenpg.jackson2example.model.money.Money;

/**
 * The deserializer half of the custom {@link Money} type.
 *
 * <p><b>Migration note:</b> base class {@code JsonDeserializer<T>} becomes
 * {@code tools.jackson.databind.ValueDeserializer<T>} in Jackson 3 (same
 * "Value" rename as {@code MoneySerializer}'s {@code ValueSerializer}).
 * {@code DeserializationContext} keeps its name unchanged in Jackson 3 -
 * only the serialization-side context class was renamed
 * ({@code SerializerProvider -> SerializationContext}).
 *
 * <p>Just like {@link MoneySerializer}, {@link #deserialize} here declares
 * a CHECKED {@code throws IOException}; the Jackson 3 equivalent declares
 * the UNCHECKED {@code tools.jackson.core.JacksonException} instead.
 */
public class MoneyDeserializer extends JsonDeserializer<Money> {

    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Parse "12.99 USD" back into amount + currency.
        String raw = p.getValueAsString();
        int spaceIndex = raw.lastIndexOf(' ');
        BigDecimal amount = new BigDecimal(raw.substring(0, spaceIndex));
        String currency = raw.substring(spaceIndex + 1);
        return new Money(amount, currency);
    }
}
