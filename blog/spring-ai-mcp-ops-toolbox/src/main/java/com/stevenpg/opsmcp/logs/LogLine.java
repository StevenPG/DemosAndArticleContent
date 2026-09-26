package com.stevenpg.opsmcp.logs;

import java.time.Instant;

/** One captured log event, flattened to what an agent can actually use. */
public record LogLine(Instant timestamp, String level, String logger, String thread, String message, String error) {
}
