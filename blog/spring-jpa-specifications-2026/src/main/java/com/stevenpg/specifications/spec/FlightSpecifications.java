package com.stevenpg.specifications.spec;

import com.stevenpg.specifications.domain.Aircraft_;
import com.stevenpg.specifications.domain.Airline;
import com.stevenpg.specifications.domain.Airline_;
import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.Amenity;
import com.stevenpg.specifications.domain.Amenity_;
import com.stevenpg.specifications.domain.Booking;
import com.stevenpg.specifications.domain.Booking_;
import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.CargoFlight;
import com.stevenpg.specifications.domain.CargoFlight_;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.Flight_;
import com.stevenpg.specifications.domain.LoyaltyTier;
import com.stevenpg.specifications.domain.PassengerFlight;
import com.stevenpg.specifications.domain.PassengerFlight_;
import com.stevenpg.specifications.domain.Route_;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.UpdateSpecification;

/**
 * The cookbook. Every method here is a reusable, composable fragment of a WHERE clause.
 * <p>
 * Two things to notice about the shape of this class:
 * <ol>
 * <li>Nothing returns {@code null}. A filter that should not apply is the caller's problem,
 * not the specification's — see {@link FlightSpecificationBuilder}. Spring Data JPA 4.0
 * removed null-tolerance from {@code and} / {@code or} / {@code where}, so a stray null is
 * now an {@code IllegalArgumentException} instead of a silently-ignored filter.</li>
 * <li>Anything that can be a {@link PredicateSpecification} is one. That interface only
 * receives a {@code From} and a {@code CriteriaBuilder}, which makes it usable against a
 * root, a join, or a correlated subquery — and reusable for updates and deletes.</li>
 * </ol>
 */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    // ---------------------------------------------------------------------------------
    // 1. Plain column predicates.
    //    PredicateSpecification takes (From, CriteriaBuilder) — no CriteriaQuery — which is
    //    what lets the same fragment be reused in a select, an update, and a delete.
    // ---------------------------------------------------------------------------------

    public static PredicateSpecification<Flight> hasStatus(FlightStatus status) {
        return (flight, cb) -> cb.equal(flight.get(Flight_.status), status);
    }

    public static PredicateSpecification<Flight> statusIn(Collection<FlightStatus> statuses) {
        return (flight, cb) -> flight.get(Flight_.status).in(statuses);
    }

    public static PredicateSpecification<Flight> priceBetween(BigDecimal min, BigDecimal max) {
        return (flight, cb) -> cb.between(flight.get(Flight_.basePrice), min, max);
    }

    public static PredicateSpecification<Flight> priceAtMost(BigDecimal max) {
        return (flight, cb) -> cb.lessThanOrEqualTo(flight.get(Flight_.basePrice), max);
    }

    public static PredicateSpecification<Flight> departsBetween(Instant from, Instant to) {
        return (flight, cb) -> cb.between(flight.get(Flight_.departureTime), from, to);
    }

    public static PredicateSpecification<Flight> departsAfter(Instant threshold) {
        return (flight, cb) -> cb.greaterThan(flight.get(Flight_.departureTime), threshold);
    }

    // ---------------------------------------------------------------------------------
    // 2. Navigating an @Embedded value type.
    //    root.get(route).get(origin) — the metamodel generates Route_ for embeddables too.
    // ---------------------------------------------------------------------------------

    public static PredicateSpecification<Flight> flyingFrom(String originCode) {
        return (flight, cb) -> cb.equal(flight.get(Flight_.route).get(Route_.origin), originCode);
    }

    public static PredicateSpecification<Flight> flyingTo(String destinationCode) {
        return (flight, cb) -> cb.equal(flight.get(Flight_.route).get(Route_.destination), destinationCode);
    }

    public static PredicateSpecification<Flight> shorterThan(int maxDistanceKm) {
        return (flight, cb) -> cb.lessThan(flight.get(Flight_.route).get(Route_.distanceKm), maxDistanceKm);
    }

    // ---------------------------------------------------------------------------------
    // 3. Joins.
    //    A to-one join is safe to repeat; a to-many join multiplies rows, which is why the
    //    collection filters below are written as EXISTS subqueries instead.
    // ---------------------------------------------------------------------------------

    /** Filters on a {@code @ManyToOne} by navigating the path — Hibernate emits an inner join. */
    public static PredicateSpecification<Flight> operatedBy(String airlineIataCode) {
        return (flight, cb) -> cb.equal(flight.get(Flight_.airline).get(Airline_.iataCode), airlineIataCode);
    }

    /** Same idea, but with an explicit join so several predicates can share one alias. */
    public static PredicateSpecification<Flight> inAlliance(Alliance alliance) {
        return (flight, cb) -> {
            Join<Flight, Airline> airline = flight.join(Flight_.airline, JoinType.INNER);
            return cb.equal(airline.get(Airline_.alliance), alliance);
        };
    }

    public static PredicateSpecification<Flight> aircraftModelIs(String model) {
        return (flight, cb) -> cb.equal(flight.get(Flight_.aircraft).get(Aircraft_.model), model);
    }

    public static PredicateSpecification<Flight> seatCapacityAtLeast(int minSeats) {
        return (flight, cb) -> cb.ge(flight.get(Flight_.aircraft).get(Aircraft_.seatCapacity), minSeats);
    }

    // ---------------------------------------------------------------------------------
    // 4. Collection membership without row multiplication.
    //    "Has amenity X" as a join returns one row per matching amenity. As an EXISTS
    //    subquery it returns one row per flight — no distinct, no broken page counts.
    // ---------------------------------------------------------------------------------

    public static Specification<Flight> hasAmenity(String amenityCode) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Flight> correlated = sub.correlate(root);
            SetJoin<Flight, Amenity> amenity = correlated.join(Flight_.amenities);
            sub.select(cb.literal(1L));
            sub.where(cb.equal(amenity.get(Amenity_.code), amenityCode));
            return cb.exists(sub);
        };
    }

    /**
     * "Has every one of these amenities." The naive join version of this is a classic bug:
     * {@code amenity.code IN ('WIFI','POWER')} matches a flight with only one of them.
     */
    public static Specification<Flight> hasAllAmenities(Collection<String> amenityCodes) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Flight> correlated = sub.correlate(root);
            SetJoin<Flight, Amenity> amenity = correlated.join(Flight_.amenities);
            sub.select(cb.countDistinct(amenity.get(Amenity_.code)));
            sub.where(amenity.get(Amenity_.code).in(amenityCodes));
            return cb.equal(sub, (long) amenityCodes.size());
        };
    }

    /** The join-based form, kept so the guide can show what it costs. Needs distinct. */
    public static Specification<Flight> hasAmenityViaJoin(String amenityCode) {
        return (root, query, cb) -> {
            SetJoin<Flight, Amenity> amenity = root.join(Flight_.amenities, JoinType.INNER);
            query.distinct(true);
            return cb.equal(amenity.get(Amenity_.code), amenityCode);
        };
    }

    // ---------------------------------------------------------------------------------
    // 5. Subqueries over a @OneToMany.
    // ---------------------------------------------------------------------------------

    /** Uncorrelated shape: count bookings per flight and compare. */
    public static Specification<Flight> hasAtLeastBookings(long minBookings) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Booking> booking = sub.from(Booking.class);
            sub.select(cb.count(booking));
            sub.where(cb.equal(booking.get(Booking_.flight), root));
            return cb.ge(sub, minBookings);
        };
    }

    /** Correlated shape: {@code sub.correlate(root)} reuses the outer alias. */
    public static Specification<Flight> averageFareAbove(double minAverageFare) {
        return (root, query, cb) -> {
            Subquery<Double> sub = query.subquery(Double.class);
            Root<Flight> correlated = sub.correlate(root);
            ListJoin<Flight, Booking> booking = correlated.join(Flight_.bookings);
            sub.select(cb.avg(booking.get(Booking_.farePaid)));
            return cb.greaterThan(sub, minAverageFare);
        };
    }

    public static Specification<Flight> bookedByTier(LoyaltyTier tier) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Booking> booking = sub.from(Booking.class);
            sub.select(cb.literal(1L));
            sub.where(cb.and(
                    cb.equal(booking.get(Booking_.flight), root),
                    cb.equal(booking.get(Booking_.loyaltyTier), tier)));
            return cb.exists(sub);
        };
    }

    /** NOT EXISTS. Reads far better than a left join with an is-null check. */
    public static Specification<Flight> hasNoBookings() {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Booking> booking = sub.from(Booking.class);
            sub.select(cb.literal(1L));
            sub.where(cb.equal(booking.get(Booking_.flight), root));
            return cb.not(cb.exists(sub));
        };
    }

    // ---------------------------------------------------------------------------------
    // 6. Inheritance: type() and treat().
    // ---------------------------------------------------------------------------------

    /** {@code root.type()} maps to the discriminator column — no treat() needed. */
    public static PredicateSpecification<Flight> isPassengerFlight() {
        return (flight, cb) -> cb.equal(flight.type(), cb.literal(PassengerFlight.class));
    }

    public static PredicateSpecification<Flight> isCargoFlight() {
        return (flight, cb) -> cb.equal(flight.type(), cb.literal(CargoFlight.class));
    }

    /**
     * Downcasting with {@code treat()} to reach a subclass attribute. The predicate below is
     * only meaningful for rows that really are passenger flights, so callers should AND it
     * with {@link #isPassengerFlight()} — {@code treat()} narrows the path, not the result set.
     */
    public static Specification<Flight> withSeatsAvailable(int minSeats) {
        return (root, query, cb) -> {
            Root<PassengerFlight> passenger = cb.treat(root, PassengerFlight.class);
            return cb.ge(passenger.get(PassengerFlight_.seatsAvailable), minSeats);
        };
    }

    /** {@code isMember} against an {@code @ElementCollection} on the subclass. */
    public static Specification<Flight> offersCabinClass(CabinClass cabinClass) {
        return (root, query, cb) -> {
            Root<PassengerFlight> passenger = cb.treat(root, PassengerFlight.class);
            return cb.isMember(cabinClass, passenger.get(PassengerFlight_.cabinClasses));
        };
    }

    public static Specification<Flight> cargoPayloadAtLeast(int minPayloadKg) {
        return (root, query, cb) -> {
            Root<CargoFlight> cargo = cb.treat(root, CargoFlight.class);
            return cb.ge(cargo.get(CargoFlight_.maxPayloadKg), minPayloadKg);
        };
    }

    // ---------------------------------------------------------------------------------
    // 7. Database functions through cb.function(), and Hibernate's own builder extensions.
    // ---------------------------------------------------------------------------------

    /**
     * "Morning departures only." {@code date_part} is PostgreSQL's, reached through the
     * portable {@code CriteriaBuilder.function} escape hatch — this is how you use a function
     * JPA never standardised without dropping to native SQL.
     */
    public static PredicateSpecification<Flight> departsBetweenHours(int fromHourUtc, int toHourUtc) {
        return (flight, cb) -> {
            Expression<Double> hour = cb.function(
                    "date_part", Double.class,
                    cb.literal("hour"),
                    flight.get(Flight_.departureTime));
            return cb.between(hour, (double) fromHourUtc, (double) toHourUtc);
        };
    }

    /** Reads a key out of the {@code jsonb} metadata column. */
    public static PredicateSpecification<Flight> metadataStringEquals(String key, String value) {
        return (flight, cb) -> {
            Expression<String> extracted = cb.function(
                    "jsonb_extract_path_text", String.class,
                    flight.get(Flight_.metadata),
                    cb.literal(key));
            return cb.equal(extracted, value);
        };
    }

    /**
     * Case-insensitive match. Plain JPA forces {@code cb.like(cb.lower(x), pattern.toLowerCase())};
     * Hibernate's builder has {@code ilike} natively. The cast is always safe on Hibernate —
     * the {@code CriteriaBuilder} handed to a Specification is a {@link HibernateCriteriaBuilder}.
     */
    public static PredicateSpecification<Flight> flightNumberMatches(String pattern) {
        return (flight, cb) -> {
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            return hcb.ilike(flight.get(Flight_.flightNumber), pattern);
        };
    }

    /** The portable equivalent, for anyone not on Hibernate. */
    public static PredicateSpecification<Flight> flightNumberMatchesPortable(String pattern) {
        return (flight, cb) -> cb.like(
                cb.lower(flight.get(Flight_.flightNumber)),
                pattern.toLowerCase());
    }

    // ---------------------------------------------------------------------------------
    // 8. Fetch joins — and the count-query trap.
    // ---------------------------------------------------------------------------------

    /**
     * Applies a fetch join so a list of flights does not trigger one query per airline.
     * <p>
     * The {@code getResultType()} guard is the whole point. {@code findAll(spec, pageable)}
     * runs the specification twice: once for the page and once for
     * {@code select count(f) from Flight f}. A fetch join in the count query is invalid —
     * Hibernate throws "query specified join fetching, but the owner of the fetched
     * association was not present in the select list" — so the fetch has to be skipped when
     * the result type is Long.
     * <p>
     * A cleaner fix for the same problem is an {@code @EntityGraph} on a derived query, which
     * Spring Data applies to the page query only. Use a fetch-joining Specification when the
     * graph you need depends on runtime input.
     */
    public static Specification<Flight> fetchAirlineAndAircraft() {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch(Flight_.airline, JoinType.LEFT);
                root.fetch(Flight_.aircraft, JoinType.LEFT);
            }
            // Contributes no restriction. Since 4.0 this is expressed by returning null,
            // exactly as Specification.unrestricted() does.
            return null;
        };
    }

    /** The same thing done wrong, kept so the guide can show the exception it produces. */
    public static Specification<Flight> fetchAirlineUnguarded() {
        return (root, query, cb) -> {
            root.fetch(Flight_.airline, JoinType.LEFT);
            return null;
        };
    }

    // ---------------------------------------------------------------------------------
    // 9. Grouping. The one place Specifications genuinely fight you.
    // ---------------------------------------------------------------------------------

    /**
     * A HAVING clause bolted onto an entity query. This works on PostgreSQL because grouping
     * by the primary key makes every other selected column functionally dependent on it, but
     * it is not portable and it quietly breaks the derived count query.
     * <p>
     * Prefer {@link #hasAtLeastBookings(long)}, which expresses the same restriction as a
     * subquery and leaves the query shape alone.
     */
    public static Specification<Flight> groupedBookingCountAtLeast(long minBookings) {
        return (root, query, cb) -> {
            ListJoin<Flight, Booking> booking = root.join(Flight_.bookings, JoinType.LEFT);
            query.groupBy(root.get(Flight_.id));
            query.having(cb.ge(cb.count(booking.get(Booking_.id)), minBookings));
            return null;
        };
    }

    // ---------------------------------------------------------------------------------
    // 10. Bulk update and delete, new in the 4.x line.
    //     These map to a single UPDATE/DELETE statement. They do NOT go through the
    //     persistence context, so entities already loaded in the current transaction keep
    //     their stale state until the context is cleared.
    // ---------------------------------------------------------------------------------

    public static UpdateSpecification<Flight> cancelFlights(PredicateSpecification<Flight> where) {
        return UpdateSpecification.<Flight>update(
                        (root, update, cb) -> update.set(Flight_.status, FlightStatus.CANCELLED))
                .where(where);
    }

    public static UpdateSpecification<Flight> repriceBy(BigDecimal multiplier, PredicateSpecification<Flight> where) {
        return UpdateSpecification.<Flight>update((root, update, cb) ->
                        update.set(Flight_.basePrice, cb.prod(root.get(Flight_.basePrice), multiplier)))
                .where(where);
    }

    public static DeleteSpecification<Flight> deleteWhere(PredicateSpecification<Flight> where) {
        return DeleteSpecification.where(where);
    }

    // ---------------------------------------------------------------------------------
    // 11. Composition helpers.
    // ---------------------------------------------------------------------------------

    /**
     * Demonstrates that a {@link PredicateSpecification} composes into a {@link Specification}
     * directly — the interfaces interoperate in both directions.
     */
    public static Specification<Flight> bookableOn(String origin, String destination, Instant notBefore) {
        return Specification.where(
                flyingFrom(origin)
                        .and(flyingTo(destination))
                        .and(departsAfter(notBefore))
                        .and(PredicateSpecification.not(hasStatus(FlightStatus.CANCELLED))));
    }

    /** Composition over a runtime-sized list, the shape a filter DSL actually needs. */
    public static Specification<Flight> anyOfAirlines(Collection<String> iataCodes) {
        return (root, query, cb) -> {
            Predicate[] alternatives = iataCodes.stream()
                    .map(code -> cb.equal(root.get(Flight_.airline).get(Airline_.iataCode), code))
                    .toArray(Predicate[]::new);
            return cb.or(alternatives);
        };
    }
}
