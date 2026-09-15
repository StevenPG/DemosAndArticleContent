package com.stevenpg.fleet.domain.alert;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record AlertEvent(Long id, String code, AlertStatus status, Instant occurredAt) {

    public static AlertEvent from(Alert entity) {
        return new AlertEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
