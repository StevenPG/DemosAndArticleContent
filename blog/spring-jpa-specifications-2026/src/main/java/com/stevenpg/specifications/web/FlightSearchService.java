package com.stevenpg.specifications.web;

import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.repository.FlightRepository;
import com.stevenpg.specifications.spec.FlightSearchCriteria;
import com.stevenpg.specifications.spec.FlightSpecificationBuilder;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightSearchService {

    private final FlightRepository flights;

    public FlightSearchService(FlightRepository flights) {
        this.flights = flights;
    }

    /**
     * Offset pagination. The fetch-join specification is composed in so the airline and
     * aircraft of every row on the page arrive in the same query; the {@code @BatchSize} on
     * {@code Flight.amenities} handles the to-many.
     */
    @Transactional(readOnly = true)
    public Page<FlightResponse> search(FlightSearchCriteria criteria, Pageable pageable) {
        Specification<Flight> spec = FlightSpecificationBuilder.from(criteria)
                .and(FlightSpecifications.fetchAirlineAndAircraft());

        return flights.findAll(spec, pageable).map(FlightResponse::from);
    }

    /**
     * The same filters, but returning a DTO projection instead of entities. Spring Data
     * narrows the SELECT list to the record's components, so nothing lazy is ever touched and
     * the read-only transaction is not strictly required.
     */
    @Transactional(readOnly = true)
    public Page<FlightSummary> searchSummaries(FlightSearchCriteria criteria, Pageable pageable) {
        Specification<Flight> spec = FlightSpecificationBuilder.from(criteria);

        return flights.findBy(spec, query -> query
                .as(FlightSummary.class)
                .page(pageable));
    }

    /**
     * Keyset pagination. {@code OFFSET 200000} makes the database walk and discard 200,000
     * rows; a keyset scroll turns the same page into an index seek on
     * {@code (departure_time, id)}.
     * <p>
     * The sort must end in a unique column or the cursor is ambiguous, which is why
     * {@code id} is always the tie-breaker.
     */
    @Transactional(readOnly = true)
    public Window<FlightSummary> scroll(FlightSearchCriteria criteria, ScrollPosition position, int pageSize) {
        Specification<Flight> spec = FlightSpecificationBuilder.from(criteria);

        return flights.findBy(spec, query -> query
                .as(FlightSummary.class)
                .sortBy(Sort.by("departureTime").ascending().and(Sort.by("id").ascending()))
                .limit(pageSize)
                .scroll(position));
    }

    /** Builds the cursor for the next scroll page from the caller's {@code ?after=} values. */
    public static ScrollPosition cursor(Instant afterDeparture, Long afterId) {
        if (afterDeparture == null || afterId == null) {
            return ScrollPosition.keyset();
        }
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("departureTime", afterDeparture);
        keys.put("id", afterId);
        return ScrollPosition.of(keys, KeysetScrollPosition.Direction.FORWARD);
    }
}
