package com.example.valhallajpa;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/** A value record used as an @EmbeddedId (a strongly typed primary key). */
@Embeddable
public value record OrderId(UUID value) {
    public static OrderId random() {
        return new OrderId(UUID.randomUUID());
    }
}
