package com.stevenpg.fleet.domain.incident;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface IncidentSummary {

    String getCode();

    String getName();

    IncidentStatus getStatus();

    int getCycles();
}
