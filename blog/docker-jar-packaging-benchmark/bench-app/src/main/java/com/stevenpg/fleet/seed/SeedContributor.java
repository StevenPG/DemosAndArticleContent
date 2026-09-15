package com.stevenpg.fleet.seed;

/**
 * Implemented once per generated domain package. Keeping the contract here means
 * the seed loop is one class instead of forty ApplicationRunners fighting over
 * ordering.
 */
public interface SeedContributor {

    /** Human-readable name of the domain being seeded. */
    String domain();

    /** Inserts {@code rows} rows for this domain. */
    int seed(int rows);
}
