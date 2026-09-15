package com.stevenpg.fleet.domain.flightplan;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface FlightPlanSummary {

    String getCode();

    String getName();

    FlightPlanStatus getStatus();

    int getCycles();
}
