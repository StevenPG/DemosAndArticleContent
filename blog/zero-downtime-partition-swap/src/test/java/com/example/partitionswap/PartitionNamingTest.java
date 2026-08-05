package com.example.partitionswap;

import com.example.partitionswap.partition.PartitionNaming;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for the name/bound arithmetic every DDL statement depends on. */
class PartitionNamingTest {

    @Test
    void nameEncodesUtcMinute() {
        Instant instant = Instant.parse("2026-08-05T14:32:41.500Z");
        assertThat(PartitionNaming.nameFor(instant)).isEqualTo("events_p20260805_1432");
    }

    @Test
    void boundsRoundTripThroughTheName() {
        Instant instant = Instant.parse("2026-08-05T14:32:41.500Z");
        String name = PartitionNaming.nameFor(instant);

        assertThat(PartitionNaming.lowerBoundOf(name)).isEqualTo(Instant.parse("2026-08-05T14:32:00Z"));
        assertThat(PartitionNaming.upperBoundOf(name)).isEqualTo(Instant.parse("2026-08-05T14:33:00Z"));
    }

    @Test
    void boundLiteralIsAnExplicitUtcTimestamp() {
        assertThat(PartitionNaming.boundLiteral(Instant.parse("2026-08-05T14:32:00Z")))
                .isEqualTo("'2026-08-05 14:32:00Z'");
    }

    @Test
    void rejectsForeignTableNames() {
        assertThatThrownBy(() -> PartitionNaming.lowerBoundOf("orders_p20260805_1432"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
