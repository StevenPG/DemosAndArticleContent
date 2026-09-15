package com.stevenpg.fleet.domain.aircraft;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record AircraftEvent(Long id, String code, AircraftStatus status, Instant occurredAt) {

    public static AircraftEvent from(Aircraft entity) {
        return new AircraftEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
