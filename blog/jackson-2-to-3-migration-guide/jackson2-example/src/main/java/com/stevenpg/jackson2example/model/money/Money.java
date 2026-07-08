package com.stevenpg.jackson2example.model.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A small value type with NO Jackson annotations at all - on purpose.
 *
 * <p>Jackson has no idea how to turn a {@code BigDecimal amount} +
 * {@code String currency} pair into the wire format we actually want
 * ({@code "12.99 USD"} as a single JSON string). That gap is exactly why
 * custom (de)serializers exist - see {@link com.stevenpg.jackson2example.json.MoneySerializer}
 * and {@link com.stevenpg.jackson2example.json.MoneyDeserializer}.
 */
public final class Money {

    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
