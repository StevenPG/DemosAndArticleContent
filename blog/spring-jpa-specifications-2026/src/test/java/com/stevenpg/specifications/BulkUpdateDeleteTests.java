package com.stevenpg.specifications;

import com.stevenpg.specifications.domain.Flight;
import com.stevenpg.specifications.domain.FlightStatus;
import com.stevenpg.specifications.spec.FlightSpecifications;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.UpdateSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code update(UpdateSpecification)} and {@code delete(DeleteSpecification)} — one statement
 * instead of load-then-save, and the persistence-context caveat that comes with them.
 */
class BulkUpdateDeleteTests extends AbstractPostgresTest {

    @Test
    @DisplayName("update() issues a single UPDATE and returns the affected row count")
    void bulkUpdate() {
        PredicateSpecification<Flight> target = FlightSpecifications.flyingFrom("ATL")
                .and(FlightSpecifications.hasStatus(FlightStatus.SCHEDULED));

        long expected = flights.count(target);
        long updated = flights.update(FlightSpecifications.cancelFlights(target));

        assertThat(updated).isEqualTo(expected);

        flushAndClear();
        assertThat(flights.count(target)).isZero();
    }

    @Test
    @DisplayName("an UpdateSpecification can set a column from an expression over itself")
    void bulkUpdateWithExpression() {
        PredicateSpecification<Flight> target = FlightSpecifications.flyingFrom("ORD");

        List<Flight> before = flights.findAll(target);
        assertThat(before).isNotEmpty();
        BigDecimal firstPriceBefore = before.get(0).getBasePrice();
        Long firstId = before.get(0).getId();

        long updated = flights.update(
                FlightSpecifications.repriceBy(new BigDecimal("1.10"), target));

        assertThat(updated).isEqualTo(before.size());

        flushAndClear();
        Flight after = flights.findById(firstId).orElseThrow();
        assertThat(after.getBasePrice())
                .isEqualByComparingTo(firstPriceBefore.multiply(new BigDecimal("1.10")));
    }

    @Test
    @DisplayName("a bulk update does NOT refresh entities already in the persistence context")
    void bulkUpdateLeavesLoadedEntitiesStale() {
        PredicateSpecification<Flight> target = FlightSpecifications.flyingFrom("DFW")
                .and(FlightSpecifications.hasStatus(FlightStatus.SCHEDULED));

        Flight loaded = flights.findAll(target).get(0);
        assertThat(loaded.getStatus()).isEqualTo(FlightStatus.SCHEDULED);

        flights.update(FlightSpecifications.cancelFlights(target));

        // Still SCHEDULED in memory: the UPDATE went straight to the database.
        assertThat(loaded.getStatus()).isEqualTo(FlightStatus.SCHEDULED);

        flushAndClear();
        Flight reloaded = flights.findById(loaded.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.CANCELLED);
    }

    @Test
    @DisplayName("delete() removes rows matching a PredicateSpecification in one statement")
    void bulkDelete() {
        PredicateSpecification<Flight> target = FlightSpecifications.hasStatus(FlightStatus.CANCELLED);

        long before = flights.count(target);
        assertThat(before).isPositive();

        entityManager.createQuery("delete from Booking b where b.flight.status = :status")
                .setParameter("status", FlightStatus.CANCELLED)
                .executeUpdate();

        long deleted = flights.delete(DeleteSpecification.where(target));

        assertThat(deleted).isEqualTo(before);

        flushAndClear();
        assertThat(flights.count(target)).isZero();
    }

    @Test
    @DisplayName("UpdateSpecification composes exactly like Specification does")
    void updateSpecificationComposition() {
        PredicateSpecification<Flight> delayedOutOfSeattle = FlightSpecifications.flyingFrom("SEA")
                .and(FlightSpecifications.hasStatus(FlightStatus.DELAYED));
        // SampleData already delays some flights out of SEA. The update moves the SCHEDULED
        // ones on top of them, so the count afterwards is the sum - comparing it against the
        // affected-row count alone would only pass on a dataset where nothing was delayed yet.
        long alreadyDelayed = flights.count(delayedOutOfSeattle);

        UpdateSpecification<Flight> spec = UpdateSpecification
                .<Flight>update((root, update, cb) -> update.set("status", FlightStatus.DELAYED))
                .where(FlightSpecifications.flyingFrom("SEA")
                        .and(FlightSpecifications.hasStatus(FlightStatus.SCHEDULED)));

        long updated = flights.update(spec);

        assertThat(updated).isPositive();
        flushAndClear();
        assertThat(flights.count(delayedOutOfSeattle)).isEqualTo(alreadyDelayed + updated);
    }
}
