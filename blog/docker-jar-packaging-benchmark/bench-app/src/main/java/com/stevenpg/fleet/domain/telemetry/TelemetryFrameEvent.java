package com.stevenpg.fleet.domain.telemetry;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record TelemetryFrameEvent(Long id, String code, TelemetryFrameStatus status, Instant occurredAt) {

    public static TelemetryFrameEvent from(TelemetryFrame entity) {
        return new TelemetryFrameEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
