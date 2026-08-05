package com.example.partitionswap.partition;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Naming and bound arithmetic for the per-minute partitions.
 *
 * <p>A partition covering {@code [2026-08-05T14:32:00Z, 2026-08-05T14:33:00Z)} is named
 * {@code events_p20260805_1432}. The name encodes the lower bound in UTC, which lets the
 * swap job recover a partition's range from the catalog alone — no bookkeeping table.
 *
 * <p>Partition names are interpolated into DDL strings. DDL cannot take bind parameters,
 * so every name that reaches SQL is produced (or strictly validated) here.
 */
public final class PartitionNaming {

    public static final String PREFIX = "events_p";
    public static final Duration WIDTH = Duration.ofMinutes(1);

    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter BOUND =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX").withZone(ZoneOffset.UTC);

    private PartitionNaming() {
    }

    /** Truncates an instant to the start of its minute. */
    public static Instant minuteOf(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    /** {@code events_p20260805_1432} for any instant inside that minute. */
    public static String nameFor(Instant instant) {
        return PREFIX + MINUTE.format(minuteOf(instant));
    }

    /** Inverse of {@link #nameFor}: the lower bound encoded in a partition name. */
    public static Instant lowerBoundOf(String partitionName) {
        if (!partitionName.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a managed partition: " + partitionName);
        }
        return Instant.from(MINUTE.parse(partitionName.substring(PREFIX.length())));
    }

    /** Upper (exclusive) bound encoded in a partition name. */
    public static Instant upperBoundOf(String partitionName) {
        return lowerBoundOf(partitionName).plus(WIDTH);
    }

    /** A timestamptz literal PostgreSQL accepts in partition bound clauses: {@code '2026-08-05 14:32:00Z'}. */
    public static String boundLiteral(Instant instant) {
        return "'" + BOUND.format(instant) + "'";
    }
}
