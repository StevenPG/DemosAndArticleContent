package com.stevenpg.fleet.domain.technician;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record TechnicianEvent(Long id, String code, TechnicianStatus status, Instant occurredAt) {

    public static TechnicianEvent from(Technician entity) {
        return new TechnicianEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
