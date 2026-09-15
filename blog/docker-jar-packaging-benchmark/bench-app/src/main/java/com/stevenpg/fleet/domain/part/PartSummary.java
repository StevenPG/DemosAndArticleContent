package com.stevenpg.fleet.domain.part;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface PartSummary {

    String getCode();

    String getName();

    PartStatus getStatus();

    int getCycles();
}
