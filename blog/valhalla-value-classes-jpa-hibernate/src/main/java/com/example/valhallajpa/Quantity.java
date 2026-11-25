package com.example.valhallajpa;

import jakarta.persistence.Embeddable;

/** A tiny value record (one int), the best case for JVM field flattening. */
@Embeddable
public value record Quantity(int units) {}
