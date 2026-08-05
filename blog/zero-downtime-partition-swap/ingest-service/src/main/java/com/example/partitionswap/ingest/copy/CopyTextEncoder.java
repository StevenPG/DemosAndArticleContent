package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.SensorReadingEvent;

import java.time.Instant;
import java.util.List;

/**
 * Encodes a batch of events into Postgres COPY {@code text} format: one line
 * per row, columns separated by tabs, special characters backslash-escaped
 * (see <a href="https://www.postgresql.org/docs/current/sql-copy.html">COPY</a>).
 *
 * <p>Text format keeps the wire payload human-debuggable, which matters more
 * in an example than the last few percent of throughput; the step up for a
 * hotter path is {@code FORMAT binary}, which skips text parsing entirely at
 * the cost of per-type binary encoders.
 */
public final class CopyTextEncoder {

    /** Column order must match the column list in the COPY statement. */
    public static final String COLUMNS = "id, device_id, metric, reading, recorded_at, ingested_at";

    private CopyTextEncoder() {
    }

    public static String encode(List<SensorReadingEvent> events, Instant ingestedAt) {
        // ~100 chars per row; presizing avoids StringBuilder growth on large batches.
        StringBuilder sb = new StringBuilder(events.size() * 112);
        String ingestedAtColumn = ingestedAt.toString();
        for (SensorReadingEvent event : events) {
            sb.append(event.id()).append('\t');
            appendEscaped(sb, event.deviceId()).append('\t');
            appendEscaped(sb, event.metric()).append('\t');
            sb.append(event.reading()).append('\t');
            sb.append(event.recordedAt()).append('\t');
            sb.append(ingestedAtColumn).append('\n');
        }
        return sb.toString();
    }

    /**
     * COPY text-format escaping: backslash, tab, newline and carriage return
     * are the only characters that can break a row; everything else passes
     * through verbatim (the JDBC driver handles client encoding).
     */
    private static StringBuilder appendEscaped(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb;
    }
}
