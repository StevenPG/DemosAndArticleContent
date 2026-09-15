package com.stevenpg.fleet.domain.crew;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface CrewMemberSummary {

    String getCode();

    String getName();

    CrewMemberStatus getStatus();

    int getCycles();
}
