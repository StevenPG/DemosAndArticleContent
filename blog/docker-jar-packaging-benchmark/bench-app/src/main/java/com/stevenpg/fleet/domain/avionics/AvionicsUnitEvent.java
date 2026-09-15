package com.stevenpg.fleet.domain.avionics;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record AvionicsUnitEvent(Long id, String code, AvionicsUnitStatus status, Instant occurredAt) {

    public static AvionicsUnitEvent from(AvionicsUnit entity) {
        return new AvionicsUnitEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
