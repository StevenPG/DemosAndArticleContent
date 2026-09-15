package com.stevenpg.fleet.domain.maintenance;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record MaintenanceTaskEvent(Long id, String code, MaintenanceTaskStatus status, Instant occurredAt) {

    public static MaintenanceTaskEvent from(MaintenanceTask entity) {
        return new MaintenanceTaskEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
