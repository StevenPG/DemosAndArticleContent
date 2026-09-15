package com.stevenpg.fleet.domain.supplier;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface SupplierSummary {

    String getCode();

    String getName();

    SupplierStatus getStatus();

    int getCycles();
}
