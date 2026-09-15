package com.stevenpg.fleet.domain.weather;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface WeatherObservationSummary {

    String getCode();

    String getName();

    WeatherObservationStatus getStatus();

    int getCycles();
}
