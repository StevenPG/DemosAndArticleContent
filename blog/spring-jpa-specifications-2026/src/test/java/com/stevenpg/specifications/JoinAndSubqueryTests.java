package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.LoyaltyTier;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/** Joins, EXISTS subqueries, and the row-multiplication trap they exist to avoid. */
class JoinAndSubqueryTests extends AbstractPostgresTest {

    @Test
    @DisplayName("navigating a @ManyToOne path emits an inner join")
    void toOneNavigation() {
        List<Flight> delta = flights.findAll(FlightSpecifications.operatedBy("DL"));

        assertThat(delta).isNotEmpty();
        assertThat(delta).allSatisfy(f -> assertThat(f.getAirline().getIataCode()).isEqualTo("DL"));
    }

    @Test
    @DisplayName("an explicit join reads the same and shares one alias across predicates")
    void explicitJoin() {
        List<Flight> starAlliance = flights.findAll(FlightSpecifications.inAlliance(Alliance.STAR_ALLIANCE));

        assertThat(starAlliance).isNotEmpty();
        assertThat(starAlliance)
                .allSatisfy(f -> assertThat(f.getAirline().getAlliance()).isEqualTo(Alliance.STAR_ALLIANCE));
    }

    @Test
    @DisplayName("EXISTS over a @ManyToMany returns one row per flight; the join form needs distinct")
    void existsBeatsJoinForCollections() {
        long viaExists = flights.count(FlightSpecifications.hasAmenity("WIFI"));
        List<Flight> viaJoin = flights.findAll(FlightSpecifications.hasAmenityViaJoin("WIFI"));

        // Same flights either way — but only because hasAmenityViaJoin sets distinct(true).
        assertThat(viaJoin).hasSize((int) viaExists);
        assertThat(viaJoin).extracting(Flight::getId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("'has ALL of these amenities' needs a counting subquery, not an IN list")
    void hasAllAmenities() {
        List<String> required = List.of("WIFI", "POWER");

        List<Flight> all = flights.findAll(FlightSpecifications.hasAllAmenities(required));
        long eitherOne = flights.count(
                Specification.anyOf(
                        FlightSpecifications.hasAmenity("WIFI"),
                        FlightSpecifications.hasAmenity("POWER")));

        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(flight -> assertThat(
                flight.getAmenities().stream().map(a -> a.getCode()).toList())
                .containsAll(required));
        // The distinction the naive IN-list version gets wrong.
        assertThat((long) all.size()).isLessThan(eitherOne);
    }

    @Test
    @DisplayName("uncorrelated count subquery over a @OneToMany")
    void countSubquery() {
        List<Flight> busy = flights.findAll(FlightSpecifications.hasAtLeastBookings(4));

        assertThat(busy).isNotEmpty();
        assertThat(busy).allSatisfy(f -> assertThat(f.getBookings()).hasSizeGreaterThanOrEqualTo(4));
    }

    @Test
    @DisplayName("correlated subquery: average fare on this flight, compared per row")
    void correlatedSubquery() {
        List<Flight> pricey = flights.findAll(FlightSpecifications.averageFareAbove(400.0));

        assertThat(pricey).isNotEmpty();
        assertThat(pricey).allSatisfy(flight -> {
            double average = flight.getBookings().stream()
                    .mapToDouble(b -> b.getFarePaid().doubleValue())
                    .average()
                    .orElse(0);
            assertThat(average).isGreaterThan(400.0);
        });
    }

    @Test
    @DisplayName("EXISTS with an extra predicate on the correlated side")
    void existsWithFilter() {
        List<Flight> platinum = flights.findAll(FlightSpecifications.bookedByTier(LoyaltyTier.PLATINUM));

        assertThat(platinum).isNotEmpty();
        assertThat(platinum).allSatisfy(flight -> assertThat(flight.getBookings())
                .anySatisfy(b -> assertThat(b.getLoyaltyTier()).isEqualTo(LoyaltyTier.PLATINUM)));
    }

    @Test
    @DisplayName("NOT EXISTS reads better than a left join plus is-null")
    void notExists() {
        List<Flight> empty = flights.findAll(FlightSpecifications.hasNoBookings());
        long withBookings = flights.count(FlightSpecifications.hasAtLeastBookings(1));

        assertThat(empty).allSatisfy(f -> assertThat(f.getBookings()).isEmpty());
        assertThat(empty.size() + withBookings).isEqualTo(120);
    }
}
