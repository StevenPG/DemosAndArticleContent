package com.stevenpg.jackson3example.json;

import tools.jackson.databind.module.SimpleModule;

import com.stevenpg.jackson3example.model.money.Money;

/**
 * Bundles the {@link Money} (de)serializer pair into one registerable unit.
 *
 * <p><b>Migration note:</b> {@code SimpleModule} keeps its name, but its
 * supertype changes: in Jackson 2 it extends
 * {@code com.fasterxml.jackson.databind.Module}; here it extends
 * {@code tools.jackson.databind.JacksonModule} - the generic "Module" name
 * was dropped in favor of "JacksonModule" specifically to stop colliding
 * with {@code java.lang.Module} from the Java Platform Module System.
 */
public class MoneyModule extends SimpleModule {

    public MoneyModule() {
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
    }
}
