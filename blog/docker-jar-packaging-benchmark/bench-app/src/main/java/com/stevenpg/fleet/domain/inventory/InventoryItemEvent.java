package com.stevenpg.fleet.domain.inventory;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record InventoryItemEvent(Long id, String code, InventoryItemStatus status, Instant occurredAt) {

    public static InventoryItemEvent from(InventoryItem entity) {
        return new InventoryItemEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
