package com.stevenpg.fleet.domain.inventory;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface InventoryItemSummary {

    String getCode();

    String getName();

    InventoryItemStatus getStatus();

    int getCycles();
}
