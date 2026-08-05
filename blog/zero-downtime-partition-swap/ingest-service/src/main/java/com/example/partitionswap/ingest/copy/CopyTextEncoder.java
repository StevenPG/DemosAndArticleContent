package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.SensorReadingEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Encodes rows into Postgres COPY {@code text} format — one line per row, tab
 * separated, backslash-escaped (see
 * <a href="https://www.postgresql.org/docs/current/sql-copy.html">COPY</a>) —
 * directly as UTF-8 bytes in a growable buffer that the caller flushes to the
 * server in chunks.
 *
 * <p>Encoding to bytes rather than building a {@code String} is the difference
 * between bounded and unbounded memory on the write path. A 10,000-row batch
 * rendered to a String is a multi-megabyte {@code char[]} (two bytes per
 * character), which then gets encoded to UTF-8 on the way out — two full
 * copies of every batch, per thread, straight into the young generation. Here
 * the buffer is allocated once per thread, reused for every batch, and drained
 * whenever it crosses {@link #FLUSH_THRESHOLD}, so peak footprint is a fixed
 * ~64 KB per writer no matter how large the batch is.
 *
 * <p>Instances are NOT thread-safe by design: each listener thread owns one.
 */
final class CopyTextEncoder {

    /** Column order must match the column list in the COPY statement. */
    static final String COLUMNS = "id, device_id, metric, reading, recorded_at, ingested_at";

    /** Drain to the server once the buffer passes this mark, keeping memory flat. */
    static final int FLUSH_THRESHOLD = 64 * 1024;

    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    /** Headroom so a single row never needs a capacity check mid-field. */
    private byte[] buffer = new byte[FLUSH_THRESHOLD + 8 * 1024];
    private int length;

    byte[] buffer() {
        return buffer;
    }

    int length() {
        return length;
    }

    void reset() {
        length = 0;
    }

    /**
     * @param ingestedAt pre-encoded arrival timestamp — identical for every row
     *                   in the batch, so it is formatted once by the caller
     *                   rather than once per row
     */
    void appendRow(SensorReadingEvent event, byte[] ingestedAt) {
        ensureCapacity(128 + event.deviceId().length() * 3 + event.metric().length() * 3);
        appendUuid(event.id());
        appendByte('\t');
        appendEscaped(event.deviceId());
        appendByte('\t');
        appendEscaped(event.metric());
        appendByte('\t');
        // Double.toString and Instant.toString each allocate a String per row.
        // They are the next allocations to kill if profiling says so — via a
        // Ryū-style double formatter and a hand-rolled ISO-8601 writer — but
        // they are bounded, short-lived, and vastly cheaper than the
        // whole-payload copy this class exists to avoid.
        appendAscii(Double.toString(event.reading()));
        appendByte('\t');
        appendAscii(event.recordedAt().toString());
        appendByte('\t');
        appendBytes(ingestedAt);
        appendByte('\n');
    }

    /** Formats the batch-wide arrival timestamp once, for {@link #appendRow}. */
    static byte[] encodeTimestamp(Instant instant) {
        return instant.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // --- field writers --------------------------------------------------

    /**
     * Writes the 36-character canonical form straight from the UUID's two
     * longs, skipping the String that {@link UUID#toString()} would allocate
     * for every single row.
     */
    private void appendUuid(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        appendHex(msb >>> 32, 8);
        appendByte('-');
        appendHex(msb >>> 16, 4);
        appendByte('-');
        appendHex(msb, 4);
        appendByte('-');
        appendHex(lsb >>> 48, 4);
        appendByte('-');
        appendHex(lsb, 12);
    }

    private void appendHex(long value, int digits) {
        for (int shift = (digits - 1) * 4; shift >= 0; shift -= 4) {
            buffer[length++] = HEX[(int) ((value >>> shift) & 0xF)];
        }
    }

    /**
     * COPY text-format escaping: backslash, tab, newline and carriage return
     * are the only characters that can break a row. ASCII takes a
     * byte-per-char fast path; anything else falls back to UTF-8 encoding for
     * that character alone.
     */
    private void appendEscaped(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> {
                    appendByte('\\');
                    appendByte('\\');
                }
                case '\t' -> {
                    appendByte('\\');
                    appendByte('t');
                }
                case '\n' -> {
                    appendByte('\\');
                    appendByte('n');
                }
                case '\r' -> {
                    appendByte('\\');
                    appendByte('r');
                }
                default -> {
                    if (c < 0x80) {
                        buffer[length++] = (byte) c;
                    } else {
                        // Rare path: let the JDK handle surrogate pairs and
                        // multi-byte sequences correctly.
                        appendBytes(String.valueOf(
                                Character.isHighSurrogate(c) && i + 1 < value.length()
                                        ? new char[]{c, value.charAt(++i)}
                                        : new char[]{c})
                                .getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    private void appendAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            buffer[length++] = (byte) value.charAt(i);
        }
    }

    private void appendBytes(byte[] bytes) {
        System.arraycopy(bytes, 0, buffer, length, bytes.length);
        length += bytes.length;
    }

    private void appendByte(char c) {
        buffer[length++] = (byte) c;
    }

    private void ensureCapacity(int additional) {
        if (length + additional > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, length + additional));
        }
    }
}
