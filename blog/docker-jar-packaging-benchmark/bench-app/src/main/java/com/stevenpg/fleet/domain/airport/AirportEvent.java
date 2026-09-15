package com.stevenpg.fleet.domain.airport;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record AirportEvent(Long id, String code, AirportStatus status, Instant occurredAt) {

    public static AirportEvent from(Airport entity) {
        return new AirportEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
