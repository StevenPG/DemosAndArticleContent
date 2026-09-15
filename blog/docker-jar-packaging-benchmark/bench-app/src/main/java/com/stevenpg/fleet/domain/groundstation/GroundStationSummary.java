package com.stevenpg.fleet.domain.groundstation;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface GroundStationSummary {

    String getCode();

    String getName();

    GroundStationStatus getStatus();

    int getCycles();
}
