package com.example.partitionswap.ingest.copy;

/**
 * The batch itself is bad — malformed values, a constraint violation, a
 * schema mismatch. Retrying will fail identically forever, so the batch is
 * routed to the dead-letter topic instead of blocking the partition.
 */
public class PoisonBatchException extends RuntimeException {

    public PoisonBatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
