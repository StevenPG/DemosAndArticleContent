package com.stevenpg.fleet.domain.sensor;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record SensorEvent(Long id, String code, SensorStatus status, Instant occurredAt) {

    public static SensorEvent from(Sensor entity) {
        return new SensorEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
