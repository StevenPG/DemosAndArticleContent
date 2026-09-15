package com.stevenpg.fleet.support;

import java.time.Instant;
import java.util.List;

/** Error payload returned by {@link GlobalExceptionHandler}. */
public record ApiError(Instant timestamp, int status, String error, String path, List<String> details) {

    public static ApiError of(int status, String error, String path, List<String> details) {
        return new ApiError(Instant.now(), status, error, path, details);
    }
}
