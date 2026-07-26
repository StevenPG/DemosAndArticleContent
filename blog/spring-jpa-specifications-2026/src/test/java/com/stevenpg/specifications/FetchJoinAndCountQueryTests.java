package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The single most common production bug with Specifications: a fetch join that also lands in
 * the derived count query.
 */
class FetchJoinAndCountQueryTests extends AbstractPostgresTest {

    @Test
    @DisplayName("a guarded fetch join initialises the to-one associations on the page")
    void guardedFetchJoinWorksWithPagination() {
        Specification<Flight> spec = Specification
                .where(FlightSpecifications.flyingFrom("ATL"))
                .and(FlightSpecifications.fetchAirlineAndAircraft());

        Page<Flight> page = flights.findAll(spec, PageRequest.of(0, 5, Sort.by("departureTime")));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getTotalElements()).isPositive();
        assertThat(page.getContent()).allSatisfy(flight -> {
            assertThat(Hibernate.isInitialized(flight.getAirline())).isTrue();
            assertThat(Hibernate.isInitialized(flight.getAircraft())).isTrue();
        });
    }

    @Test
    @DisplayName("the same fetch join without the getResultType() guard blows up the count query")
    void unguardedFetchJoinFailsWithPagination() {
        Specification<Flight> spec = Specification
                .where(FlightSpecifications.flyingFrom("ATL"))
                .and(FlightSpecifications.fetchAirlineUnguarded());

        assertThatThrownBy(() -> flights.findAll(spec, PageRequest.of(0, 5)).getContent())
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("the unguarded version is fine as long as nothing derives a count query")
    void unguardedFetchJoinIsFineWithoutPagination() {
        Specification<Flight> spec = Specification
                .where(FlightSpecifications.flyingFrom("ATL"))
                .and(FlightSpecifications.fetchAirlineUnguarded());

        List<Flight> found = flights.findAll(spec);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(f -> assertThat(Hibernate.isInitialized(f.getAirline())).isTrue());
    }

    @Test
    @DisplayName("a separate count specification lets the page and the count diverge on purpose")
    void separateCountSpecification() {
        Specification<Flight> page = Specification
                .where(FlightSpecifications.flyingFrom("ATL"))
                .and(FlightSpecifications.fetchAirlineAndAircraft());
        Specification<Flight> count = Specification.where(FlightSpecifications.flyingFrom("ATL"));

        Page<Flight> result = flights.findAll(page, count, PageRequest.of(0, 5, Sort.by("departureTime")));

        assertThat(result.getTotalElements()).isEqualTo(flights.count(count));
    }

    @Test
    @DisplayName("the join form only matches the EXISTS form because it sets distinct(true)")
    void toManyJoinNeedsDistinctToAgreeWithExists() {
        long viaExists = flights.count(FlightSpecifications.hasAmenity("WIFI"));
        long viaJoin = flights.count(FlightSpecifications.hasAmenityViaJoin("WIFI"));

        // Spring Data does honour distinct when it derives the count query — it switches to
        // countDistinct(root). So these agree. The catch is that you pay for a distinct over
        // the whole join on every page and every count, and you only get the right answer
        // because someone remembered the distinct(true) call. EXISTS needs neither.
        assertThat(viaJoin).isEqualTo(viaExists);
    }
}
