package com.stevenpg.specifications;

import com.stevenpg.specifications.config.SampleData;
import com.stevenpg.specifications.domain.Alliance;
import com.stevenpg.specifications.domain.CabinClass;
import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.domain.PassengerFlight;
import com.stevenpg.specifications.spec.FlightSearchCriteria;
import com.stevenpg.specifications.spec.FlightSpecificationBuilder;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of the whole exercise: one repository method serving every combination of twenty
 * optional filters.
 */
class DynamicSearchTests extends AbstractPostgresTest {

    @Test
    @DisplayName("an empty search form matches every flight")
    void emptyCriteriaMatchesEverything() {
        Specification<Flight> spec = FlightSpecificationBuilder.from(FlightSearchCriteria.empty());

        assertThat(flights.count(spec)).isEqualTo(120);
    }

    @Test
    @DisplayName("each supplied filter can only narrow the result set")
    void filtersAreMonotonic() {
        FlightSearchCriteria justOrigin = withOrigin("ATL");
        FlightSearchCriteria originAndPrice = new FlightSearchCriteria(
                "ATL", null, null, null, null, null, BigDecimal.valueOf(300), null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        long broad = flights.count(FlightSpecificationBuilder.from(justOrigin));
        long narrow = flights.count(FlightSpecificationBuilder.from(originAndPrice));

        assertThat(broad).isPositive();
        assertThat(narrow).isLessThanOrEqualTo(broad);
    }

    @Test
    @DisplayName("a fully-populated form composes into one query and still returns rows")
    void everyFilterAtOnce() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "ATL",
                null,
                SampleData.BASE.minus(1, ChronoUnit.DAYS),
                null,
                0,
                23,
                BigDecimal.valueOf(1000),
                List.of("DL", "UA", "AA"),
                null,
                null,
                List.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED),
                List.of("WIFI"),
                CabinClass.ECONOMY,
                1,
                20_000,
                null,
                null,
                null,
                null,
                Boolean.TRUE);

        List<Flight> found = flights.findAll(FlightSpecificationBuilder.from(criteria));

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(flight -> {
            assertThat(flight).isInstanceOf(PassengerFlight.class);
            assertThat(flight.getRoute().getOrigin()).isEqualTo("ATL");
            assertThat(flight.getBasePrice()).isLessThanOrEqualTo(BigDecimal.valueOf(1000));
            assertThat(flight.getStatus()).isIn(FlightStatus.SCHEDULED, FlightStatus.DELAYED);
            assertThat(flight.getAirline().getIataCode()).isIn("DL", "UA", "AA");
            assertThat(flight.getAmenities().stream().map(a -> a.getCode())).contains("WIFI");
            assertThat(((PassengerFlight) flight).getCabinClasses()).contains(CabinClass.ECONOMY);
            assertThat(((PassengerFlight) flight).getSeatsAvailable()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    @DisplayName("filtering by alliance reaches through the airline join")
    void allianceFilter() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                null, null, null, null, null, null, null, null, Alliance.ONEWORLD, null,
                null, null, null, null, null, null, null, null, null, null);

        List<Flight> found = flights.findAll(FlightSpecificationBuilder.from(criteria));

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(f -> assertThat(f.getAirline().getAlliance())
                .isEqualTo(Alliance.ONEWORLD));
    }

    @Test
    @DisplayName("a jsonb filter composes with the rest exactly like a column filter")
    void jsonbFilterComposes() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "ATL", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, "Starlink", null);

        List<Flight> found = flights.findAll(FlightSpecificationBuilder.from(criteria));

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(flight -> {
            assertThat(flight.getRoute().getOrigin()).isEqualTo("ATL");
            assertThat(flight.getMetadata()).containsEntry("wifiVendor", "Starlink");
        });
    }

    private static FlightSearchCriteria withOrigin(String origin) {
        return new FlightSearchCriteria(
                origin, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
