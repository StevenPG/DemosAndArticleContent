package com.example.broken;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Scenario: a value class used directly as an @Entity. */
@Entity
public value class ValuePointEntity {
    @Id
    private final Long id;

    private final int x;
    private final int y;

    protected ValuePointEntity() {
        this.id = null;
        this.x = 0;
        this.y = 0;
    }

    public ValuePointEntity(Long id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public Long id() {
        return id;
    }

    public int x() {
        return x;
    }
}
