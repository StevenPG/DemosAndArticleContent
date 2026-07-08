package com.stevenpg.jackson3example.model.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A small value type with NO Jackson annotations at all - identical to the
 * jackson2-example sibling class. Custom types need a custom (de)serializer
 * in both Jackson versions; see {@link com.stevenpg.jackson3example.json.MoneySerializer}
 * and {@link com.stevenpg.jackson3example.json.MoneyDeserializer} for what
 * changes there.
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
