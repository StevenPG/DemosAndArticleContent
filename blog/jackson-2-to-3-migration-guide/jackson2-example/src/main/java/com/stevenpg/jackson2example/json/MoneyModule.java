package com.stevenpg.jackson2example.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.stevenpg.jackson2example.model.money.Money;

/**
 * Bundles the {@link Money} (de)serializer pair into one registerable unit.
 *
 * <p><b>Migration note:</b> {@code SimpleModule} keeps its name in Jackson 3,
 * but its supertype changes: in Jackson 2 it extends
 * {@code com.fasterxml.jackson.databind.Module}; in Jackson 3 it extends
 * {@code tools.jackson.databind.JacksonModule} (the generic "Module" name
 * was dropped in favor of "JacksonModule" to avoid clashing with
 * {@code java.lang.Module} from the Java Platform Module System - a
 * decade-old naming collision Jackson 3 finally resolves).
 */
public class MoneyModule extends SimpleModule {

    public MoneyModule() {
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
    }
}
