package com.stevenpg.fleet.domain.incident;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record IncidentEvent(Long id, String code, IncidentStatus status, Instant occurredAt) {

    public static IncidentEvent from(Incident entity) {
        return new IncidentEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
