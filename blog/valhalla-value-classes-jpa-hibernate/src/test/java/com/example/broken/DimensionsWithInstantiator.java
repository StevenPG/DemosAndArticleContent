package com.example.broken;

import jakarta.persistence.Embeddable;
import org.hibernate.annotations.Instantiator;

/**
 * Scenario: the same non-record value class, but with Hibernate's
 * {@link Instantiator} pointing at the all-args constructor, and no no-arg one.
 */
@Embeddable
public value class DimensionsWithInstantiator {
    private int widthMm;
    private int heightMm;

    @Instantiator({"widthMm", "heightMm"})
    public DimensionsWithInstantiator(int widthMm, int heightMm) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }

    public int widthMm() {
        return widthMm;
    }

    public int heightMm() {
        return heightMm;
    }
}
