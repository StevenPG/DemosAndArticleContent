package com.stevenpg.fleet.domain.fuel;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record FuelUpliftEvent(Long id, String code, FuelUpliftStatus status, Instant occurredAt) {

    public static FuelUpliftEvent from(FuelUplift entity) {
        return new FuelUpliftEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
