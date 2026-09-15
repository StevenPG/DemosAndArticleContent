package com.stevenpg.fleet.domain.workorder;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface WorkOrderSummary {

    String getCode();

    String getName();

    WorkOrderStatus getStatus();

    int getCycles();
}
