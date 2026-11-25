package com.example.valhallajpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Benchmark entity with identical columns, using plain identity records. */
@Entity
public class IdentityLine {
    @Id
    private Long id;

    private IdentityMoney price;

    private IdentityQuantity qty;

    protected IdentityLine() {}

    public IdentityLine(Long id, IdentityMoney price, IdentityQuantity qty) {
        this.id = id;
        this.price = price;
        this.qty = qty;
    }

    public long lineTotalCents() {
        return price.amountCents() * qty.units();
    }
}
