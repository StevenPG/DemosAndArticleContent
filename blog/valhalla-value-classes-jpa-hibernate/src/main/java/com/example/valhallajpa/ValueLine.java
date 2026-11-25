package com.example.valhallajpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Benchmark entity whose embeddables are value records. */
@Entity
public class ValueLine {
    @Id
    private Long id;

    private Money price;

    private Quantity qty;

    protected ValueLine() {}

    public ValueLine(Long id, Money price, Quantity qty) {
        this.id = id;
        this.price = price;
        this.qty = qty;
    }

    public long lineTotalCents() {
        return price.amountCents() * qty.units();
    }
}
