package com.stevenpg.fleet.domain.telemetry;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface TelemetryFrameSummary {

    String getCode();

    String getName();

    TelemetryFrameStatus getStatus();

    int getCycles();
}
