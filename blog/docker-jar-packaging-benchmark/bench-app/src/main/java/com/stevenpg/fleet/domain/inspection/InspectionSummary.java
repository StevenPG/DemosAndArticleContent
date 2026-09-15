package com.stevenpg.fleet.domain.inspection;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface InspectionSummary {

    String getCode();

    String getName();

    InspectionStatus getStatus();

    int getCycles();
}
