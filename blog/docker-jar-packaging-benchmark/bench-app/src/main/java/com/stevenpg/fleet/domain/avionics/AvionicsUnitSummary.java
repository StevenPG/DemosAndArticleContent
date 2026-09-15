package com.stevenpg.fleet.domain.avionics;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface AvionicsUnitSummary {

    String getCode();

    String getName();

    AvionicsUnitStatus getStatus();

    int getCycles();
}
