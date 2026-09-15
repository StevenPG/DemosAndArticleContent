package com.stevenpg.fleet.domain.airport;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface AirportSummary {

    String getCode();

    String getName();

    AirportStatus getStatus();

    int getCycles();
}
