package com.stevenpg.specifications.config;

import com.stevenpg.specifications.domain.Aircraft;
import com.stevenpg.specifications.domain.Airline;
import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.Amenity;
import com.stevenpg.specifications.domain.Booking;
import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.CargoFlight;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.LoyaltyTier;
import com.stevenpg.specifications.domain.PassengerFlight;
import com.stevenpg.specifications.domain.Route;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a deterministic dataset. Shared by the demo runner and every test, so the numbers
 * quoted in the blog post are reproducible.
 */
public final class SampleData {

    /** Fixed instant so date-based assertions never depend on when the suite runs. */
    public static final Instant BASE = Instant.parse("2026-08-01T00:00:00Z");

    private static final String[] AIRPORTS = {"ATL", "ORD", "DFW", "DEN", "LAX", "JFK", "SEA", "MIA"};

    private SampleData() {
    }

    /** Persists the dataset. */
    public static void load(EntityManager em) {
        Dataset dataset = build();
        dataset.airlines().forEach(em::persist);
        dataset.aircraft().forEach(em::persist);
        dataset.amenities().forEach(em::persist);
        dataset.flights().forEach(em::persist);
        em.flush();
        em.clear();
    }

    /** The reference dataset, built with no persistence involved so it can be inspected offline. */
    public static Dataset build() {

        Airline delta = new Airline("DL", "Delta Air Lines", "US", Alliance.SKYTEAM);
        Airline united = new Airline("UA", "United Airlines", "US", Alliance.STAR_ALLIANCE);
        Airline american = new Airline("AA", "American Airlines", "US", Alliance.ONEWORLD);
        Airline freight = new Airline("FX", "FedEx Express", "US", Alliance.UNALIGNED);
        List<Airline> airlines = List.of(delta, united, american, freight);

        Aircraft a320 = new Aircraft("Airbus", "A320neo", 180, 6300);
        Aircraft b738 = new Aircraft("Boeing", "737-800", 189, 5400);
        Aircraft b789 = new Aircraft("Boeing", "787-9", 296, 14000);
        Aircraft md11 = new Aircraft("Boeing", "MD-11F", 0, 12500);
        List<Aircraft> fleet = List.of(a320, b738, b789, md11);

        Amenity wifi = new Amenity("WIFI", "In-flight wifi");
        Amenity power = new Amenity("POWER", "Seat power");
        Amenity meal = new Amenity("MEAL", "Hot meal");
        Amenity lieFlat = new Amenity("LIE_FLAT", "Lie-flat seat");
        List<Amenity> amenities = List.of(wifi, power, meal, lieFlat);

        List<Airline> passengerAirlines = List.of(delta, united, american);
        List<Aircraft> passengerFleet = List.of(a320, b738, b789);
        List<Amenity> amenityPool = List.of(wifi, power, meal, lieFlat);

        Random random = new Random(20260801L);
        List<Flight> created = new ArrayList<>();

        for (int i = 0; i < 120; i++) {
            String origin = AIRPORTS[i % AIRPORTS.length];
            String destination = AIRPORTS[(i * 3 + 1) % AIRPORTS.length];
            if (origin.equals(destination)) {
                destination = AIRPORTS[(i + 2) % AIRPORTS.length];
            }

            Instant departure = BASE.plus(i, ChronoUnit.HOURS);
            Instant arrival = departure.plus(Duration.ofMinutes(95 + (i % 7) * 40));
            Route route = new Route(origin, destination, 600 + (i % 12) * 450);
            BigDecimal price = BigDecimal.valueOf(120 + (i % 9) * 45L);

            Flight flight;
            if (i % 10 == 9) {
                flight = new CargoFlight("FX" + (100 + i), route, departure, arrival,
                        price.multiply(BigDecimal.valueOf(4)), freight, md11,
                        40_000 + (i % 5) * 5_000, i % 20 == 9);
            } else {
                Airline airline = passengerAirlines.get(i % passengerAirlines.size());
                Aircraft aircraft = passengerFleet.get(i % passengerFleet.size());
                PassengerFlight passenger = new PassengerFlight(
                        airline.getIataCode() + (1000 + i), route, departure, arrival,
                        price, airline, aircraft, 4 + (i % 60));

                passenger.addCabinClass(CabinClass.ECONOMY);
                if (i % 3 == 0) {
                    passenger.addCabinClass(CabinClass.BUSINESS);
                }
                if (i % 12 == 0) {
                    passenger.addCabinClass(CabinClass.FIRST);
                }
                flight = passenger;
            }

            for (int a = 0; a < amenityPool.size(); a++) {
                if ((i + a) % 3 != 0) {
                    flight.addAmenity(amenityPool.get(a));
                }
            }

            flight.putMetadata("wifiVendor", i % 4 == 0 ? "Starlink" : "Viasat");
            flight.putMetadata("gate", "C" + (i % 40));

            if (i % 17 == 5) {
                flight.setStatus(FlightStatus.CANCELLED);
            } else if (i % 13 == 4) {
                flight.setStatus(FlightStatus.DELAYED);
            }

            int bookingCount = i % 6;
            for (int b = 0; b < bookingCount; b++) {
                LoyaltyTier tier = LoyaltyTier.values()[random.nextInt(LoyaltyTier.values().length)];
                flight.addBooking(new Booking(
                        "Passenger " + i + "-" + b,
                        flight.getBasePrice().add(BigDecimal.valueOf(random.nextInt(200))),
                        tier,
                        departure.minus(Duration.ofDays(1 + random.nextInt(60)))));
            }

            created.add(flight);
        }

        return new Dataset(airlines, fleet, amenities, created);
    }

    /** The pieces of the reference dataset, in insertion order. */
    public record Dataset(
            List<Airline> airlines,
            List<Aircraft> aircraft,
            List<Amenity> amenities,
            List<Flight> flights) {
    }
}
