package com.example.partitionswap.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartitionsTest {

    @Test
    void windowIsMinuteAlignedAndHalfOpen() {
        Instant inside = Instant.parse("2026-08-05T14:32:41.123Z");
        PartitionWindow window = Partitions.windowFor(inside);

        assertEquals(Instant.parse("2026-08-05T14:32:00Z"), window.start());
        assertEquals(Instant.parse("2026-08-05T14:33:00Z"), window.end());
        assertEquals("sensor_readings_p20260805_1432", window.tableName());
    }

    @Test
    void everyInstantInTheSameMinuteMapsToTheSameTable() {
        PartitionWindow first = Partitions.windowFor(Instant.parse("2026-08-05T14:32:00.000Z"));
        PartitionWindow last = Partitions.windowFor(Instant.parse("2026-08-05T14:32:59.999Z"));
        PartitionWindow next = Partitions.windowFor(Instant.parse("2026-08-05T14:33:00.000Z"));

        assertEquals(first, last);
        assertEquals(first.end(), next.start());
    }

    @Test
    void tableNameRoundTrips() {
        PartitionWindow original = Partitions.windowFor(Instant.parse("2026-12-31T23:59:59Z"));
        Optional<PartitionWindow> parsed = Partitions.parseTableName(original.tableName());

        assertEquals(Optional.of(original), parsed);
    }

    @Test
    void foreignTableNamesAreRejected() {
        assertTrue(Partitions.parseTableName("sensor_readings").isEmpty());
        assertTrue(Partitions.parseTableName("sensor_readings_pdefault").isEmpty());
        assertTrue(Partitions.parseTableName("orders_p20260805_1432").isEmpty());
        assertTrue(Partitions.parseTableName(null).isEmpty());
    }

    @Test
    void sqlLiteralsAreUtcAndPostgresParseable() {
        PartitionWindow window = Partitions.windowFor(Instant.parse("2026-08-05T14:32:41Z"));

        assertEquals("2026-08-05 14:32:00+00:00", window.startLiteral());
        assertEquals("2026-08-05 14:33:00+00:00", window.endLiteral());
    }
}
