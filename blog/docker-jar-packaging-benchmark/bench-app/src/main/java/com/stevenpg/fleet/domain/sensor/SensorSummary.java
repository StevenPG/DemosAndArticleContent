package com.stevenpg.fleet.domain.sensor;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface SensorSummary {

    String getCode();

    String getName();

    SensorStatus getStatus();

    int getCycles();
}
