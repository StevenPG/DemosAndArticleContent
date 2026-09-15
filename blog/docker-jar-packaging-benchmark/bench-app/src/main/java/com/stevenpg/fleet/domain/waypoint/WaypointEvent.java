package com.stevenpg.fleet.domain.waypoint;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record WaypointEvent(Long id, String code, WaypointStatus status, Instant occurredAt) {

    public static WaypointEvent from(Waypoint entity) {
        return new WaypointEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
