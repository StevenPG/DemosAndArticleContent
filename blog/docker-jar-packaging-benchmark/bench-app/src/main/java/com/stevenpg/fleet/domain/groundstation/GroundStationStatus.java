package com.stevenpg.fleet.domain.groundstation;

public enum GroundStationStatus {
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {
        return this == ACTIVE || this == DEGRADED;
    }
}
