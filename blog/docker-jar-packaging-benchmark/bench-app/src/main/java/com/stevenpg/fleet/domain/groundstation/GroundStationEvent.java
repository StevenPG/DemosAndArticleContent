package com.stevenpg.fleet.domain.groundstation;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record GroundStationEvent(Long id, String code, GroundStationStatus status, Instant occurredAt) {

    public static GroundStationEvent from(GroundStation entity) {
        return new GroundStationEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
