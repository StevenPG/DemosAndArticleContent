package com.stevenpg.fleet.domain.fuel;

public enum FuelUpliftStatus {
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {
        return this == ACTIVE || this == DEGRADED;
    }
}
