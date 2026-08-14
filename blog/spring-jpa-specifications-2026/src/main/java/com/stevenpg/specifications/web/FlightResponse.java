package com.stevenpg.specifications.web;

import com.stevenpg.specifications.domain.CargoFlight;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.PassengerFlight;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FlightResponse(
        Long id,
        String type,
        String flightNumber,
        String origin,
        String destination,
        int distanceKm,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal basePrice,
        FlightStatus status,
        String airline,
        String aircraft,
        List<String> amenities,
        Integer seatsAvailable,
        Integer maxPayloadKg) {

    public static FlightResponse from(Flight flight) {
        Integer seats = flight instanceof PassengerFlight passenger ? passenger.getSeatsAvailable() : null;
        Integer payload = flight instanceof CargoFlight cargo ? cargo.getMaxPayloadKg() : null;

        return new FlightResponse(
                flight.getId(),
                flight instanceof PassengerFlight ? "PASSENGER" : "CARGO",
                flight.getFlightNumber(),
                flight.getRoute().getOrigin(),
                flight.getRoute().getDestination(),
                flight.getRoute().getDistanceKm(),
                flight.getDepartureTime(),
                flight.getArrivalTime(),
                flight.getBasePrice(),
                flight.getStatus(),
                flight.getAirline().getIataCode(),
                flight.getAircraft().getModel(),
                flight.getAmenities().stream().map(a -> a.getCode()).sorted().toList(),
                seats,
                payload);
    }
}
