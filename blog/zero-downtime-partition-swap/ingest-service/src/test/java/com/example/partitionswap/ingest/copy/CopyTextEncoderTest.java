package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.SensorReadingEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CopyTextEncoderTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-08-05T14:32:05.123456Z");

    @Test
    void encodesOneTabSeparatedLinePerRow() {
        UUID id = UUID.fromString("0198b2a0-0000-7000-8000-000000000001");
        SensorReadingEvent event = new SensorReadingEvent(
                id, "device-001", "temperature_c", 21.5, Instant.parse("2026-08-05T14:32:04Z"));

        String encoded = CopyTextEncoder.encode(List.of(event), INGESTED_AT);

        assertThat(encoded).isEqualTo(
                id + "\tdevice-001\ttemperature_c\t21.5\t2026-08-05T14:32:04Z\t2026-08-05T14:32:05.123456Z\n");
    }

    @Test
    void escapesCopyTextSpecialCharacters() {
        SensorReadingEvent hostile = new SensorReadingEvent(
                UUID.randomUUID(), "dev\tice\\1", "temp\nera\rture", 1.0, INGESTED_AT);

        String encoded = CopyTextEncoder.encode(List.of(hostile), INGESTED_AT);

        assertThat(encoded).contains("dev\\tice\\\\1");
        assertThat(encoded).contains("temp\\nera\\rture");
        // Exactly 5 real tabs (6 columns) and one real newline survive.
        assertThat(encoded.chars().filter(c -> c == '\t').count()).isEqualTo(5);
        assertThat(encoded.chars().filter(c -> c == '\n').count()).isEqualTo(1);
    }

    @Test
    void allRowsInABatchShareTheIngestTimestamp() {
        List<SensorReadingEvent> events = List.of(
                new SensorReadingEvent(UUID.randomUUID(), "a", "m", 1.0, Instant.now()),
                new SensorReadingEvent(UUID.randomUUID(), "b", "m", 2.0, Instant.now()));

        String encoded = CopyTextEncoder.encode(events, INGESTED_AT);

        assertThat(encoded.lines())
                .hasSize(2)
                .allMatch(line -> line.endsWith("\t" + INGESTED_AT));
    }
}
