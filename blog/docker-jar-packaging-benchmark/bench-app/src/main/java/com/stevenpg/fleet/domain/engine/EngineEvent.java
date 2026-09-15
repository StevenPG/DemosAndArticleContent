package com.stevenpg.fleet.domain.engine;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record EngineEvent(Long id, String code, EngineStatus status, Instant occurredAt) {

    public static EngineEvent from(Engine entity) {
        return new EngineEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
