package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Composition, and the Spring Data JPA 4.0 change that breaks the old way of doing it. */
class CompositionTests extends AbstractPostgresTest {

    @Test
    @DisplayName("unrestricted() is the neutral element: it matches everything")
    void unrestrictedMatchesEverything() {
        long all = flights.count(Specification.unrestricted());

        assertThat(all).isEqualTo(120);
    }

    @Test
    @DisplayName("Specification.where(null) throws on 4.0 — it used to mean 'no filter'")
    void whereNullNowThrows() {
        // A bare Specification.where(null) does not even compile any more: 4.0 added a
        // where(PredicateSpecification) overload, so the null literal is ambiguous. Once you
        // add the cast the compiler wants, you get the runtime failure instead.
        assertThatThrownBy(() -> Specification.where((Specification<Flight>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("and(null) throws too, so accumulator loops have to start from unrestricted()")
    void andNullNowThrows() {
        Specification<Flight> spec = Specification.unrestricted();

        assertThatThrownBy(() -> spec.and((Specification<Flight>) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("and() narrows, or() widens")
    void andOrCompose() {
        PredicateSpecification<Flight> cheap =
                FlightSpecifications.priceAtMost(BigDecimal.valueOf(200));
        PredicateSpecification<Flight> cancelled =
                FlightSpecifications.hasStatus(FlightStatus.CANCELLED);

        long cheapCount = flights.count(cheap);
        long cancelledCount = flights.count(cancelled);
        long both = flights.count(cheap.and(cancelled));
        long either = flights.count(cheap.or(cancelled));

        assertThat(both).isLessThanOrEqualTo(Math.min(cheapCount, cancelledCount));
        assertThat(either).isEqualTo(cheapCount + cancelledCount - both);
    }

    @Test
    @DisplayName("allOf and anyOf fold a runtime-sized list without a manual loop")
    void allOfAnyOf() {
        List<PredicateSpecification<Flight>> filters = List.of(
                FlightSpecifications.flyingFrom("ATL"),
                FlightSpecifications.hasStatus(FlightStatus.SCHEDULED));

        long conjunction = flights.count(PredicateSpecification.allOf(filters));
        long disjunction = flights.count(PredicateSpecification.anyOf(filters));

        assertThat(conjunction).isLessThanOrEqualTo(disjunction);
        assertThat(conjunction).isPositive();
    }

    @Test
    @DisplayName("a PredicateSpecification composes straight into a Specification")
    void predicateSpecificationInteroperates() {
        Specification<Flight> spec = Specification
                .where(FlightSpecifications.flyingFrom("ATL"))
                .and(FlightSpecifications.hasStatus(FlightStatus.SCHEDULED))
                .and(FlightSpecifications.hasAmenity("WIFI"));

        List<Flight> found = flights.findAll(spec);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(flight -> {
            assertThat(flight.getRoute().getOrigin()).isEqualTo("ATL");
            assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        });
    }

    @Test
    @DisplayName("not() inverts, and not(unrestricted()) is still unrestricted")
    void negation() {
        long cancelled = flights.count(FlightSpecifications.hasStatus(FlightStatus.CANCELLED));
        long notCancelled = flights.count(
                PredicateSpecification.not(FlightSpecifications.hasStatus(FlightStatus.CANCELLED)));

        assertThat(cancelled + notCancelled).isEqualTo(120);
        assertThat(flights.count(Specification.not(Specification.unrestricted()))).isEqualTo(120);
    }
}
