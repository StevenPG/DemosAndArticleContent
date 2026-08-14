package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.CargoFlight;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.PassengerFlight;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/** Inheritance ({@code type()} / {@code treat()}), embeddables, and database functions. */
class InheritanceAndFunctionTests extends AbstractPostgresTest {

    @Test
    @DisplayName("root.type() filters on the discriminator column")
    void filterByType() {
        List<Flight> passenger = flights.findAll(FlightSpecifications.isPassengerFlight());
        List<Flight> cargo = flights.findAll(FlightSpecifications.isCargoFlight());

        assertThat(passenger).allSatisfy(f -> assertThat(f).isInstanceOf(PassengerFlight.class));
        assertThat(cargo).allSatisfy(f -> assertThat(f).isInstanceOf(CargoFlight.class));
        assertThat(passenger.size() + cargo.size()).isEqualTo(120);
    }

    @Test
    @DisplayName("treat() reaches a subclass attribute, but does not filter by type on its own")
    void treatNarrowsThePathNotTheResultSet() {
        Specification<Flight> guarded = Specification
                .where(FlightSpecifications.isPassengerFlight())
                .and(FlightSpecifications.withSeatsAvailable(40));

        List<Flight> found = flights.findAll(guarded);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(flight -> {
            assertThat(flight).isInstanceOf(PassengerFlight.class);
            assertThat(((PassengerFlight) flight).getSeatsAvailable()).isGreaterThanOrEqualTo(40);
        });
    }

    @Test
    @DisplayName("isMember against an @ElementCollection of enums")
    void elementCollectionMembership() {
        Specification<Flight> firstClass = Specification
                .where(FlightSpecifications.isPassengerFlight())
                .and(FlightSpecifications.offersCabinClass(CabinClass.FIRST));

        List<Flight> found = flights.findAll(firstClass);

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(flight -> assertThat(((PassengerFlight) flight).getCabinClasses())
                .contains(CabinClass.FIRST));
    }

    @Test
    @DisplayName("treat() into the other subclass")
    void cargoSubclassAttribute() {
        Specification<Flight> heavy = Specification
                .where(FlightSpecifications.isCargoFlight())
                .and(FlightSpecifications.cargoPayloadAtLeast(50_000));

        List<Flight> found = flights.findAll(heavy);

        assertThat(found).allSatisfy(flight -> assertThat(((CargoFlight) flight).getMaxPayloadKg())
                .isGreaterThanOrEqualTo(50_000));
    }

    @Test
    @DisplayName("an @Embedded value type is navigated with two get() calls")
    void embeddedNavigation() {
        List<Flight> shortHops = flights.findAll(
                FlightSpecifications.flyingFrom("ATL").and(FlightSpecifications.shorterThan(2000)));

        assertThat(shortHops).isNotEmpty();
        assertThat(shortHops).allSatisfy(flight -> {
            assertThat(flight.getRoute().getOrigin()).isEqualTo("ATL");
            assertThat(flight.getRoute().getDistanceKm()).isLessThan(2000);
        });
    }

    @Test
    @DisplayName("cb.function() reaches PostgreSQL's date_part without dropping to native SQL")
    void databaseFunction() {
        List<Flight> mornings = flights.findAll(FlightSpecifications.departsBetweenHours(6, 11));

        assertThat(mornings).isNotEmpty();
        assertThat(mornings).allSatisfy(flight -> {
            int hour = flight.getDepartureTime().atZone(ZoneOffset.UTC).getHour();
            assertThat(hour).isBetween(6, 11);
        });
    }

    @Test
    @DisplayName("cb.function() reads a key out of a jsonb column")
    void jsonbExtraction() {
        List<Flight> starlink = flights.findAll(
                FlightSpecifications.metadataStringEquals("wifiVendor", "Starlink"));

        assertThat(starlink).isNotEmpty();
        assertThat(starlink).allSatisfy(flight -> assertThat(flight.getMetadata())
                .containsEntry("wifiVendor", "Starlink"));
    }

    @Test
    @DisplayName("Hibernate's ilike matches the portable lower()+like form")
    void hibernateIlike() {
        List<Flight> viaIlike = flights.findAll(FlightSpecifications.flightNumberMatches("dl10%"));
        List<Flight> viaPortable = flights.findAll(FlightSpecifications.flightNumberMatchesPortable("dl10%"));

        assertThat(viaIlike).isNotEmpty();
        assertThat(viaIlike).extracting(Flight::getId)
                .containsExactlyInAnyOrderElementsOf(viaPortable.stream().map(Flight::getId).toList());
    }
}
