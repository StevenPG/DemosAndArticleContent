package com.example.valhallajpa;

import jakarta.persistence.Embeddable;

/** The same shape as Money, but a plain (identity) record, for comparison. */
@Embeddable
public record IdentityMoney(long amountCents, String currency) {}
