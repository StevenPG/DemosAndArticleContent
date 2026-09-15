package com.stevenpg.fleet.domain.part;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record PartEvent(Long id, String code, PartStatus status, Instant occurredAt) {

    public static PartEvent from(Part entity) {
        return new PartEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
