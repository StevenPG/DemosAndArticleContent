package com.stevenpg.fleet.domain.runway;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record RunwayEvent(Long id, String code, RunwayStatus status, Instant occurredAt) {

    public static RunwayEvent from(Runway entity) {
        return new RunwayEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
