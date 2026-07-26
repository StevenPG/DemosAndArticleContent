package com.stevenpg.specifications.spec;

import com.stevenpg.specifications.domain.Flight;

import java.util.Collection;
import java.util.function.Function;

import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a {@link FlightSearchCriteria} into a single {@link Specification}.
 * <p>
 * This class is the whole reason the guide's spine is a search endpoint. It shows the one
 * pattern that matters in production code: start from a neutral element and fold every filter
 * that the caller actually supplied into it.
 * <p>
 * <strong>The neutral element changed in Spring Data JPA 4.0.</strong> The idiom everyone
 * wrote for a decade —
 * <pre>{@code
 * Specification<Flight> spec = Specification.where(null);   // throws on 4.0
 * }</pre>
 * now fails fast with {@code IllegalArgumentException: Specification must not be null}, and so
 * does {@code spec.and(null)}. {@link Specification#unrestricted()} is the replacement: a
 * specification that contributes no predicate and is elided from the final expression.
 */
public final class FlightSpecificationBuilder {

    private FlightSpecificationBuilder() {
    }

    public static Specification<Flight> from(FlightSearchCriteria criteria) {

        Specification<Flight> spec = Specification.unrestricted();

        spec = and(spec, criteria.origin(), FlightSpecifications::flyingFrom);
        spec = and(spec, criteria.destination(), FlightSpecifications::flyingTo);
        spec = and(spec, criteria.departingAfter(), FlightSpecifications::departsAfter);
        spec = and(spec, criteria.maxPrice(), FlightSpecifications::priceAtMost);
        spec = and(spec, criteria.alliance(), FlightSpecifications::inAlliance);
        spec = and(spec, criteria.aircraftModel(), FlightSpecifications::aircraftModelIs);
        spec = and(spec, criteria.maxDistanceKm(), FlightSpecifications::shorterThan);
        spec = and(spec, criteria.flightNumberLike(), FlightSpecifications::flightNumberMatches);

        if (criteria.departingBefore() != null && criteria.departingAfter() != null) {
            spec = spec.and(FlightSpecifications.departsBetween(
                    criteria.departingAfter(), criteria.departingBefore()));
        }

        if (criteria.earliestDepartureHourUtc() != null && criteria.latestDepartureHourUtc() != null) {
            spec = spec.and(FlightSpecifications.departsBetweenHours(
                    criteria.earliestDepartureHourUtc(), criteria.latestDepartureHourUtc()));
        }

        if (isNotEmpty(criteria.airlineCodes())) {
            spec = spec.and(FlightSpecifications.anyOfAirlines(criteria.airlineCodes()));
        }

        if (isNotEmpty(criteria.statuses())) {
            spec = spec.and(FlightSpecifications.statusIn(criteria.statuses()));
        }

        if (isNotEmpty(criteria.requiredAmenities())) {
            spec = spec.and(FlightSpecifications.hasAllAmenities(criteria.requiredAmenities()));
        }

        if (criteria.wifiVendor() != null) {
            spec = spec.and(FlightSpecifications.metadataStringEquals("wifiVendor", criteria.wifiVendor()));
        }

        if (criteria.bookedByTier() != null) {
            spec = spec.and(FlightSpecifications.bookedByTier(criteria.bookedByTier()));
        }

        if (criteria.minBookings() != null) {
            spec = spec.and(FlightSpecifications.hasAtLeastBookings(criteria.minBookings()));
        }

        // Anything that reaches into PassengerFlight has to be guarded by the discriminator,
        // because treat() narrows the path but not the result set.
        if (criteria.cabinClass() != null) {
            spec = spec.and(FlightSpecifications.isPassengerFlight())
                    .and(FlightSpecifications.offersCabinClass(criteria.cabinClass()));
        }

        if (criteria.minSeatsAvailable() != null) {
            spec = spec.and(FlightSpecifications.isPassengerFlight())
                    .and(FlightSpecifications.withSeatsAvailable(criteria.minSeatsAvailable()));
        }

        if (Boolean.TRUE.equals(criteria.passengerOnly())) {
            spec = spec.and(FlightSpecifications.isPassengerFlight());
        }

        return spec;
    }

    /**
     * Applies {@code factory} only when {@code value} is present. Keeping the null check here,
     * once, is what lets every method on {@link FlightSpecifications} assume its arguments are
     * real and return a real predicate.
     */
    private static <V> Specification<Flight> and(
            Specification<Flight> spec, V value, Function<V, PredicateSpecification<Flight>> factory) {
        return value == null ? spec : spec.and(factory.apply(value));
    }

    private static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }
}
