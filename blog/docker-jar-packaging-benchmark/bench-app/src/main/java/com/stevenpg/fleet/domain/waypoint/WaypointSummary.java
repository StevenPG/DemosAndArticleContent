package com.stevenpg.fleet.domain.waypoint;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface WaypointSummary {

    String getCode();

    String getName();

    WaypointStatus getStatus();

    int getCycles();
}
