package com.example.partitionswap.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * One minute-aligned partition slot of the {@code sensor_readings} table:
 * the half-open interval {@code [start, end)} and the physical table that
 * holds its rows while detached (staging) and after attach (partition).
 *
 * <p>The bounds are rendered as SQL literals because {@code ATTACH PARTITION}
 * and {@code CHECK} constraints are DDL — they cannot take bind parameters.
 * Every value interpolated into DDL in this project comes from this record,
 * which only ever produces machine-generated table names and ISO-8601
 * timestamps, never user input.
 */
public record PartitionWindow(Instant start, Instant end, String tableName) {

    private static final DateTimeFormatter SQL_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssxxx").withZone(ZoneOffset.UTC);

    /** Lower bound as a Postgres timestamptz literal body, e.g. {@code 2026-08-05 14:32:00+00:00}. */
    public String startLiteral() {
        return SQL_TIMESTAMP.format(start);
    }

    /** Upper (exclusive) bound as a Postgres timestamptz literal body. */
    public String endLiteral() {
        return SQL_TIMESTAMP.format(end);
    }
}
