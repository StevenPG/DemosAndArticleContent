package com.stevenpg.fleet.domain.technician;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface TechnicianSummary {

    String getCode();

    String getName();

    TechnicianStatus getStatus();

    int getCycles();
}
