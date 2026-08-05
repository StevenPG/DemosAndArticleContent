package com.example.partitionswap.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The naming contract between the ingest service (which creates and fills
 * per-minute staging tables) and the maintenance service (which indexes and
 * attaches them). Both sides derive the same physical table name from the
 * same UTC minute, so no coordination channel other than the database itself
 * is needed: the maintenance service discovers work by scanning the catalog
 * for tables matching this prefix that are not yet attached to the parent.
 */
public final class Partitions {

    /** The partitioned parent — the only table readers ever query. */
    public static final String PARENT_TABLE = "sensor_readings";

    /** Prefix for per-minute physical tables, e.g. {@code sensor_readings_p20260805_1432}. */
    public static final String TABLE_PREFIX = "sensor_readings_p";

    private static final DateTimeFormatter MINUTE_SUFFIX =
            DateTimeFormatter.ofPattern("uuuuMMdd_HHmm").withZone(ZoneOffset.UTC);

    private Partitions() {
    }

    /** The partition window (bounds + table name) that owns the given instant. */
    public static PartitionWindow windowFor(Instant instant) {
        Instant start = instant.truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(1, ChronoUnit.MINUTES);
        return new PartitionWindow(start, end, TABLE_PREFIX + MINUTE_SUFFIX.format(start));
    }

    /**
     * Reverse of {@link #windowFor}: recover the window from a physical table
     * name found in {@code pg_class}. Empty if the name is not one of ours.
     */
    public static Optional<PartitionWindow> parseTableName(String tableName) {
        if (tableName == null || !tableName.startsWith(TABLE_PREFIX)) {
            return Optional.empty();
        }
        String suffix = tableName.substring(TABLE_PREFIX.length());
        try {
            Instant start = LocalDateTime.parse(suffix, MINUTE_SUFFIX).toInstant(ZoneOffset.UTC);
            return Optional.of(windowFor(start));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
