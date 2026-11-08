package com.example.broken;

import jakarta.persistence.Embeddable;

/**
 * Scenario: a plain (non-record) value class as an embeddable, written the
 * "classic JPA" way with a no-arg constructor. Value class fields are
 * implicitly final, so Hibernate cannot set them after construction.
 */
@Embeddable
public value class Dimensions {
    private int widthMm;
    private int heightMm;

    protected Dimensions() {
        this.widthMm = 0;
        this.heightMm = 0;
    }

    public Dimensions(int widthMm, int heightMm) {
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
