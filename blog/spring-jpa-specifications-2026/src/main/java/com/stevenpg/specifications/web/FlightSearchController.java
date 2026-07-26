package com.stevenpg.specifications.web;

import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.LoyaltyTier;
import com.stevenpg.specifications.spec.FlightSearchCriteria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Twenty optional query parameters, one endpoint, one repository method. This is the argument
 * for Specifications in a single file: every parameter here would be another
 * {@code findByOriginAndDestinationAndAirlineCodeIn...} method, or another branch in a
 * hand-concatenated JPQL string.
 */
@RestController
@RequestMapping("/api/flights")
public class FlightSearchController {

    private final FlightSearchService searchService;

    public FlightSearchController(FlightSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public Page<FlightResponse> search(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) Instant departingAfter,
            @RequestParam(required = false) Instant departingBefore,
            @RequestParam(required = false) Integer earliestDepartureHourUtc,
            @RequestParam(required = false) Integer latestDepartureHourUtc,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> airlineCodes,
            @RequestParam(required = false) Alliance alliance,
            @RequestParam(required = false) String aircraftModel,
            @RequestParam(required = false) List<FlightStatus> statuses,
            @RequestParam(required = false) List<String> requiredAmenities,
            @RequestParam(required = false) CabinClass cabinClass,
            @RequestParam(required = false) Integer minSeatsAvailable,
            @RequestParam(required = false) Integer maxDistanceKm,
            @RequestParam(required = false) LoyaltyTier bookedByTier,
            @RequestParam(required = false) Long minBookings,
            @RequestParam(required = false) String flightNumberLike,
            @RequestParam(required = false) String wifiVendor,
            @RequestParam(required = false) Boolean passengerOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "departureTime") String sort) {

        FlightSearchCriteria criteria = new FlightSearchCriteria(
                origin, destination, departingAfter, departingBefore,
                earliestDepartureHourUtc, latestDepartureHourUtc, maxPrice, airlineCodes,
                alliance, aircraftModel, statuses, requiredAmenities, cabinClass,
                minSeatsAvailable, maxDistanceKm, bookedByTier, minBookings,
                flightNumberLike, wifiVendor, passengerOnly);

        return searchService.search(criteria, PageRequest.of(page, size, Sort.by(sort)));
    }

    @GetMapping("/summaries")
    public Page<FlightSummary> summaries(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        FlightSearchCriteria criteria = new FlightSearchCriteria(
                origin, destination, null, null, null, null, maxPrice, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        return searchService.searchSummaries(criteria, PageRequest.of(page, size, Sort.by("departureTime")));
    }

    /**
     * Keyset scrolling. Call it once with no cursor, then pass the last row's
     * {@code departureTime} and {@code id} back as {@code afterDeparture} / {@code afterId}.
     */
    @GetMapping("/scroll")
    public Window<FlightSummary> scroll(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) Instant afterDeparture,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "20") int size) {

        FlightSearchCriteria criteria = new FlightSearchCriteria(
                origin, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        return searchService.scroll(criteria, FlightSearchService.cursor(afterDeparture, afterId), size);
    }
}
