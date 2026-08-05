package com.example.partitionswap.ingest.copy;

/**
 * The database was unreachable, shutting down, out of resources, or otherwise
 * temporarily unable to accept the COPY. The data is fine; retry it forever.
 *
 * <p>This is the default classification for anything unrecognised, and that
 * default is deliberate: blocking a partition until an operator looks at it
 * is recoverable, while dead-lettering perfectly good rows because Postgres
 * was restarting is not.
 */
public class TransientIngestException extends RuntimeException {

    public TransientIngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
