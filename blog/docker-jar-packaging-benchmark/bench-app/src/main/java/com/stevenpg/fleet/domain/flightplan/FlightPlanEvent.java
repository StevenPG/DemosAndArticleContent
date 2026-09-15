package com.stevenpg.fleet.domain.flightplan;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record FlightPlanEvent(Long id, String code, FlightPlanStatus status, Instant occurredAt) {

    public static FlightPlanEvent from(FlightPlan entity) {
        return new FlightPlanEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
