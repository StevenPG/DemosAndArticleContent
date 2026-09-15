package com.stevenpg.fleet.domain.weather;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record WeatherObservationEvent(Long id, String code, WeatherObservationStatus status, Instant occurredAt) {

    public static WeatherObservationEvent from(WeatherObservation entity) {
        return new WeatherObservationEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
