package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.spec.FlightSpecifications;
import com.stevenpg.specifications.web.FlightSummary;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/** The fluent {@code findBy} API: projections, limits, and keyset scrolling. */
class FluentQueryAndScrollTests extends AbstractPostgresTest {

    private final Specification<Flight> fromAtlanta =
            Specification.where(FlightSpecifications.flyingFrom("ATL"));

    @Test
    @DisplayName("as() projects into a record instead of hydrating entities")
    void dtoProjection() {
        Page<FlightSummary> page = flights.findBy(fromAtlanta, query -> query
                .as(FlightSummary.class)
                .page(PageRequest.of(0, 5, Sort.by("departureTime"))));

        assertThat(page.getContent()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(page.getContent()).allSatisfy(summary -> {
            assertThat(summary.id()).isNotNull();
            assertThat(summary.flightNumber()).isNotBlank();
        });
    }

    @Test
    @DisplayName("sortBy + first() gives you 'the cheapest one' without a Pageable")
    void sortAndTakeFirst() {
        // Two Atlanta departures tie at the minimum fare, so the sort needs the id tie-breaker
        // to be deterministic — the same reason keyset scrolling insists on a unique last key.
        Sort sort = Sort.by("basePrice").ascending().and(Sort.by("id").ascending());

        Optional<Flight> cheapest = flights.findBy(fromAtlanta, query -> query
                .sortBy(sort)
                .first());

        assertThat(cheapest).isPresent();
        List<Flight> all = flights.findAll(fromAtlanta, sort);
        assertThat(cheapest.get().getId()).isEqualTo(all.get(0).getId());
    }

    @Test
    @DisplayName("limit() caps the result set at the database, not in Java")
    void limitResults() {
        List<Flight> topThree = flights.findBy(fromAtlanta, query -> query
                .sortBy(Sort.by("departureTime"))
                .limit(3)
                .all());

        assertThat(topThree).hasSize(3);
    }

    @Test
    @DisplayName("count() and exists() are terminal operations on the same fluent query")
    void countAndExists() {
        long count = flights.findBy(fromAtlanta, query -> query.count());
        boolean exists = flights.findBy(fromAtlanta, query -> query.exists());

        assertThat(count).isEqualTo(flights.count(fromAtlanta));
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("keyset scrolling walks the whole result set with no OFFSET")
    void keysetScroll() {
        Sort sort = Sort.by("departureTime").ascending().and(Sort.by("id").ascending());

        Window<FlightSummary> first = flights.findBy(fromAtlanta, query -> query
                .as(FlightSummary.class)
                .sortBy(sort)
                .limit(4)
                .scroll(ScrollPosition.keyset()));

        assertThat(first.getContent()).hasSize(4);
        assertThat(first.hasNext()).isTrue();

        ScrollPosition next = first.positionAt(first.getContent().size() - 1);
        Window<FlightSummary> second = flights.findBy(fromAtlanta, query -> query
                .as(FlightSummary.class)
                .sortBy(sort)
                .limit(4)
                .scroll(next));

        assertThat(second.getContent()).isNotEmpty();
        // No overlap: the cursor picks up strictly after the last row of page one.
        assertThat(second.getContent()).extracting(FlightSummary::id)
                .doesNotContainAnyElementsOf(first.getContent().stream().map(FlightSummary::id).toList());
    }

    @Test
    @DisplayName("scrolling forward eventually exhausts the window")
    void scrollToTheEnd() {
        Sort sort = Sort.by("departureTime").ascending().and(Sort.by("id").ascending());
        ScrollPosition position = ScrollPosition.keyset();

        int seen = 0;
        for (int guard = 0; guard < 50; guard++) {
            ScrollPosition current = position;
            Window<FlightSummary> window = flights.findBy(fromAtlanta, query -> query
                    .as(FlightSummary.class)
                    .sortBy(sort)
                    .limit(4)
                    .scroll(current));

            seen += window.getContent().size();
            if (!window.hasNext() || window.getContent().isEmpty()) {
                break;
            }
            position = window.positionAt(window.getContent().size() - 1);
        }

        assertThat(seen).isEqualTo((int) flights.count(fromAtlanta));
    }
}
