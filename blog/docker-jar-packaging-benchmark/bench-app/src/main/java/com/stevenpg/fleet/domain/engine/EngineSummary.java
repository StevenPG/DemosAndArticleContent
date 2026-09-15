package com.stevenpg.fleet.domain.engine;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface EngineSummary {

    String getCode();

    String getName();

    EngineStatus getStatus();

    int getCycles();
}
