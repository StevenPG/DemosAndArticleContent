package com.stevenpg.fleet.domain.satellite;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record SatelliteLinkEvent(Long id, String code, SatelliteLinkStatus status, Instant occurredAt) {

    public static SatelliteLinkEvent from(SatelliteLink entity) {
        return new SatelliteLinkEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
