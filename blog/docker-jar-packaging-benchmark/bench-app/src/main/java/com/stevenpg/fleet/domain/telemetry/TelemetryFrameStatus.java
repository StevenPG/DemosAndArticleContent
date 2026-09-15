package com.stevenpg.fleet.domain.telemetry;

public enum TelemetryFrameStatus {
    ACTIVE,
    DEGRADED,
    GROUNDED,
    RETIRED;

    public boolean serviceable() {
        return this == ACTIVE || this == DEGRADED;
    }
}
