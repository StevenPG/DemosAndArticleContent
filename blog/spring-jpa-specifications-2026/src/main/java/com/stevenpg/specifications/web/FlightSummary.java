package com.stevenpg.specifications.web;

import com.stevenpg.specifications.domain.FlightStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A DTO projection. Handed to {@code SpecificationFluentQuery.as(...)}, Spring Data narrows the
 * SELECT list to just these columns instead of hydrating whole {@code Flight} entities — no
 * lazy associations, no persistence-context bookkeeping, and no chance of an N+1.
 * <p>
 * The component names have to match property names on {@code Flight} for the projection to
 * bind, which is why this is flat rather than nesting the embedded route.
 */
public record FlightSummary(
        Long id,
        String flightNumber,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal basePrice,
        FlightStatus status) {
}
