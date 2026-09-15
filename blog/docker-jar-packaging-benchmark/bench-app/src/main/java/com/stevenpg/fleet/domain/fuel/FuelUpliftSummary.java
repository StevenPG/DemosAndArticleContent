package com.stevenpg.fleet.domain.fuel;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface FuelUpliftSummary {

    String getCode();

    String getName();

    FuelUpliftStatus getStatus();

    int getCycles();
}
