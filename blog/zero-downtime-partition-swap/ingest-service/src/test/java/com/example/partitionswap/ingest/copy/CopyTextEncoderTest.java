package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.SensorReadingEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CopyTextEncoderTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-08-05T14:32:05.123456Z");
    private static final byte[] INGESTED_AT_BYTES = CopyTextEncoder.encodeTimestamp(INGESTED_AT);

    private final CopyTextEncoder encoder = new CopyTextEncoder();

    private String encode(List<SensorReadingEvent> events) {
        encoder.reset();
        events.forEach(event -> encoder.appendRow(event, INGESTED_AT_BYTES));
        return new String(encoder.buffer(), 0, encoder.length(), StandardCharsets.UTF_8);
    }

    @Test
    void encodesOneTabSeparatedLinePerRow() {
        UUID id = UUID.fromString("0198b2a0-0000-7000-8000-000000000001");
        SensorReadingEvent event = new SensorReadingEvent(
                id, "device-001", "temperature_c", 21.5, Instant.parse("2026-08-05T14:32:04Z"));

        assertThat(encode(List.of(event))).isEqualTo(
                id + "\tdevice-001\ttemperature_c\t21.5\t2026-08-05T14:32:04Z\t2026-08-05T14:32:05.123456Z\n");
    }

    @Test
    void writesUuidsIdenticallyToUuidToString() {
        // The hand-rolled hex writer must agree with the JDK on every nibble,
        // including leading zeros and the high-bit values that sign-extend.
        List<SensorReadingEvent> events = IntStream.range(0, 500)
                .mapToObj(i -> new SensorReadingEvent(
                        UUID.randomUUID(), "d", "m", 1.0, INGESTED_AT))
                .toList();

        String encoded = encode(events);

        List<String> expected = events.stream().map(e -> e.id().toString()).toList();
        List<String> actual = encoded.lines().map(line -> line.substring(0, line.indexOf('\t'))).toList();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void writesEdgeCaseUuidsCorrectly() {
        UUID allZero = new UUID(0L, 0L);
        UUID allOnes = new UUID(-1L, -1L);

        String encoded = encode(List.of(
                new SensorReadingEvent(allZero, "d", "m", 1.0, INGESTED_AT),
                new SensorReadingEvent(allOnes, "d", "m", 1.0, INGESTED_AT)));

        assertThat(encoded.lines().map(l -> l.substring(0, l.indexOf('\t'))))
                .containsExactly(allZero.toString(), allOnes.toString());
    }

    @Test
    void escapesCopyTextSpecialCharacters() {
        SensorReadingEvent hostile = new SensorReadingEvent(
                UUID.randomUUID(), "dev\tice\\1", "temp\nera\rture", 1.0, INGESTED_AT);

        String encoded = encode(List.of(hostile));

        assertThat(encoded).contains("dev\\tice\\\\1");
        assertThat(encoded).contains("temp\\nera\\rture");
        // Exactly 5 real tabs (6 columns) and one real newline survive.
        assertThat(encoded.chars().filter(c -> c == '\t').count()).isEqualTo(5);
        assertThat(encoded.chars().filter(c -> c == '\n').count()).isEqualTo(1);
    }

    @Test
    void handlesMultiByteAndSurrogatePairText() {
        SensorReadingEvent unicode = new SensorReadingEvent(
                UUID.randomUUID(), "über-gerät", "🌡️-temp", 1.0, INGESTED_AT);

        String encoded = encode(List.of(unicode));

        assertThat(encoded).contains("über-gerät");
        assertThat(encoded).contains("🌡️-temp");
    }

    @Test
    void allRowsInABatchShareTheIngestTimestamp() {
        List<SensorReadingEvent> events = List.of(
                new SensorReadingEvent(UUID.randomUUID(), "a", "m", 1.0, Instant.now()),
                new SensorReadingEvent(UUID.randomUUID(), "b", "m", 2.0, Instant.now()));

        assertThat(encode(events).lines())
                .hasSize(2)
                .allMatch(line -> line.endsWith("\t" + INGESTED_AT));
    }

    @Test
    void bufferGrowsToFitAnOversizedRowAndIsReusedAcrossBatches() {
        String hugeDeviceId = "d".repeat(200_000);
        SensorReadingEvent oversized = new SensorReadingEvent(
                UUID.randomUUID(), hugeDeviceId, "m", 1.0, INGESTED_AT);

        assertThat(encode(List.of(oversized))).contains(hugeDeviceId);

        // reset() must make the grown buffer safe to reuse, not leave stale bytes.
        SensorReadingEvent small = new SensorReadingEvent(
                UUID.randomUUID(), "device-001", "m", 1.0, INGESTED_AT);
        assertThat(encode(List.of(small)).lines()).hasSize(1);
    }
}
