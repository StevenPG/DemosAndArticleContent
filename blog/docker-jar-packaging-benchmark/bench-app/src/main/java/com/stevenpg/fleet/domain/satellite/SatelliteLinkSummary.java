package com.stevenpg.fleet.domain.satellite;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface SatelliteLinkSummary {

    String getCode();

    String getName();

    SatelliteLinkStatus getStatus();

    int getCycles();
}
