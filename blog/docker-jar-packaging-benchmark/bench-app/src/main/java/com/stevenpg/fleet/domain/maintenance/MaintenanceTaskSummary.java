package com.stevenpg.fleet.domain.maintenance;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface MaintenanceTaskSummary {

    String getCode();

    String getName();

    MaintenanceTaskStatus getStatus();

    int getCycles();
}
