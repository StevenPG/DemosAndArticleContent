package com.stevenpg.fleet.domain.inventory;

public enum InventoryItemStatus {
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {
        return this == ACTIVE || this == DEGRADED;
    }
}
