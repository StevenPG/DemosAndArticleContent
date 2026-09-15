package com.stevenpg.fleet.domain.alert;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface AlertSummary {

    String getCode();

    String getName();

    AlertStatus getStatus();

    int getCycles();
}
