package com.stevenpg.fleet.domain.inspection;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record InspectionEvent(Long id, String code, InspectionStatus status, Instant occurredAt) {

    public static InspectionEvent from(Inspection entity) {
        return new InspectionEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
