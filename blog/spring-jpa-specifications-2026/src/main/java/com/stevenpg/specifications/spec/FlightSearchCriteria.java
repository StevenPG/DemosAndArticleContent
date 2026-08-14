package com.stevenpg.specifications.spec;

import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.LoyaltyTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The search form, as a record. Every component is optional; {@code null} means "the user did
 * not fill this in", which is exactly the case Specifications exist to handle.
 * <p>
 * This is the input a real search endpoint gets, and the reason a static {@code @Query} cannot
 * serve it: fifteen optional filters are 2^15 possible WHERE clauses.
 */
public record FlightSearchCriteria(
        String origin,
        String destination,
        Instant departingAfter,
        Instant departingBefore,
        Integer earliestDepartureHourUtc,
        Integer latestDepartureHourUtc,
        BigDecimal maxPrice,
        List<String> airlineCodes,
        Alliance alliance,
        String aircraftModel,
        List<FlightStatus> statuses,
        List<String> requiredAmenities,
        CabinClass cabinClass,
        Integer minSeatsAvailable,
        Integer maxDistanceKm,
        LoyaltyTier bookedByTier,
        Long minBookings,
        String flightNumberLike,
        String wifiVendor,
        Boolean passengerOnly) {

    public static FlightSearchCriteria empty() {
        return new FlightSearchCriteria(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
