package com.example.valhallajpa;

import jakarta.persistence.Embeddable;

/** A value record used as a JPA embeddable. No identity: == compares state. */
@Embeddable
public value record Money(long amountCents, String currency) {}
