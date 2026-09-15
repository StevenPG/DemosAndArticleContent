package com.stevenpg.fleet.domain.supplier;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record SupplierEvent(Long id, String code, SupplierStatus status, Instant occurredAt) {

    public static SupplierEvent from(Supplier entity) {
        return new SupplierEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
