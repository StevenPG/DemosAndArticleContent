package com.stevenpg.fleet.domain.aircraft;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface AircraftSummary {

    String getCode();

    String getName();

    AircraftStatus getStatus();

    int getCycles();
}
