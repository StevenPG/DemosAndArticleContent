package com.example.partitionswap.repository;

/** Projection for the per-type aggregation over the read table. */
public interface EventTypeCount {

    String getEventType();

    long getCount();
}
