package com.stevenpg.fleet.domain.flightplan;

public enum FlightPlanStatus {
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {
        return this == ACTIVE || this == DEGRADED;
    }
}
