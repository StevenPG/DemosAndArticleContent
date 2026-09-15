package com.stevenpg.fleet.domain.runway;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface RunwaySummary {

    String getCode();

    String getName();

    RunwayStatus getStatus();

    int getCycles();
}
