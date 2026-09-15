package com.stevenpg.fleet.domain.workorder;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record WorkOrderEvent(Long id, String code, WorkOrderStatus status, Instant occurredAt) {

    public static WorkOrderEvent from(WorkOrder entity) {
        return new WorkOrderEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
