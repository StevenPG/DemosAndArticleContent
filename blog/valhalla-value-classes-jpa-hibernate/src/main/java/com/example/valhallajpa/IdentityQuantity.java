package com.example.valhallajpa;

import jakarta.persistence.Embeddable;

/** The same shape as Quantity, but a plain (identity) record, for comparison. */
@Embeddable
public record IdentityQuantity(int units) {}
