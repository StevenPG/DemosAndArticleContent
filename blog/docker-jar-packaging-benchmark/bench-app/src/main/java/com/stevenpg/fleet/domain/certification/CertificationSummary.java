package com.stevenpg.fleet.domain.certification;

/** Closed projection - Spring Data builds the select list from these getters. */
public interface CertificationSummary {

    String getCode();

    String getName();

    CertificationStatus getStatus();

    int getCycles();
}
