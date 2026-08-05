package com.example.partitionswap.ingest.consumer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.copy.CopyBatchWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the poison-record path.
 *
 * <p>These exist because the first implementation of this listener was
 * declared {@code List<SensorReadingEvent>} and looked correct. It was not:
 * {@code ErrorHandlingDeserializer} signals an undeserializable record by
 * passing a <b>null value</b> through to the listener, so the listener threw
 * NullPointerException deep in the write path, the error handler classified
 * that as a generic retryable failure, and the partition wedged forever while
 * logging nothing useful.
 */
class SensorReadingBatchConsumerTest {

    private final CopyBatchWriter writer = mock(CopyBatchWriter.class);
    private final SensorReadingBatchConsumer consumer = new SensorReadingBatchConsumer(writer);

    private static ConsumerRecord<String, SensorReadingEvent> record(SensorReadingEvent value) {
        return new ConsumerRecord<>("sensor-readings", 0, 0L, "k", value);
    }

    private static SensorReadingEvent event() {
        return new SensorReadingEvent(UUID.randomUUID(), "device-001", "temperature_c", 1.0, Instant.now());
    }

    /**
     * Builds the record exactly as ErrorHandlingDeserializer would: a null
     * value plus the failure header, populated through the same public helper
     * the deserializer itself uses, so the test cannot drift from the real
     * header format.
     */
    private static ConsumerRecord<String, SensorReadingEvent> undeserializableRecord() {
        ConsumerRecord<String, SensorReadingEvent> failed = record(null);
        SerializationUtils.deserializationException(
                failed.headers(), "not-json".getBytes(), new IllegalStateException("bad json"), false);
        return failed;
    }

    @Test
    void writesEveryEventInAHealthyBatch() {
        List<ConsumerRecord<String, SensorReadingEvent>> records =
                List.of(record(event()), record(event()), record(event()));
        when(writer.write(anyList())).thenReturn(3L);

        consumer.onBatch(records);

        AtomicReference<List<SensorReadingEvent>> captured = new AtomicReference<>();
        verify(writer).write(org.mockito.ArgumentMatchers.argThat(list -> {
            captured.set(new ArrayList<>(list));
            return true;
        }));
        assertThat(captured.get()).hasSize(3).doesNotContainNull();
    }

    @Test
    void undeserializableRecordFailsTheBatchAtItsExactIndex() {
        List<ConsumerRecord<String, SensorReadingEvent>> records =
                List.of(record(event()), undeserializableRecord(), record(event()));

        assertThatThrownBy(() -> consumer.onBatch(records))
                .isInstanceOf(BatchListenerFailedException.class)
                // The index is what lets DefaultErrorHandler commit the records
                // before it, dead-letter this one, and redeliver the rest.
                .satisfies(thrown -> assertThat(((BatchListenerFailedException) thrown).getIndex()).isEqualTo(1));

        // Nothing is written when any record in the batch is unusable: COPY is
        // all-or-nothing, so a partial batch must never reach the database.
        verify(writer, never()).write(anyList());
    }

    @Test
    void carriesTheOriginalDeserializationExceptionAsTheCause() {
        List<ConsumerRecord<String, SensorReadingEvent>> records = List.of(undeserializableRecord());

        assertThatThrownBy(() -> consumer.onBatch(records))
                .isInstanceOf(BatchListenerFailedException.class)
                .cause().isInstanceOf(DeserializationException.class);
    }

    @Test
    void aBareNullValueStillFailsCleanlyRatherThanThrowingNullPointer() {
        // Defence in depth: even without the header (e.g. a tombstone), the
        // listener must report a failed record, never NPE inside the writer.
        List<ConsumerRecord<String, SensorReadingEvent>> records = List.of(record(null));

        assertThatThrownBy(() -> consumer.onBatch(records))
                .isInstanceOf(BatchListenerFailedException.class)
                .isNotInstanceOf(NullPointerException.class);
        verify(writer, never()).write(anyList());
    }

    @Test
    void emptyBatchIsIgnored() {
        consumer.onBatch(List.of());
        verify(writer, never()).write(anyList());
    }
}
