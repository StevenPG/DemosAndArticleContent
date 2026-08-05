package com.example.partitionswap.ingest.consumer;

import com.example.partitionswap.common.SensorReadingEvent;
import com.example.partitionswap.ingest.copy.CopyBatchWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogAccessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch listener: spring-kafka hands over everything one {@code poll()}
 * returned (up to {@code max.poll.records}) as a single list, and commits the
 * offsets only after this method returns normally. That makes the unit of
 * work batch-shaped end to end — one poll, one COPY, one offset commit — which
 * is where COPY-based ingestion gets its throughput. At a demo rate of ~1
 * msg/s the batches are small; at thousands per second the same code path
 * simply gets denser batches, with zero code changes.
 *
 * <p>The listener takes {@code ConsumerRecord}s rather than bare values for
 * one reason: {@code ErrorHandlingDeserializer} signals a record it could not
 * deserialize by passing a <b>null value</b> through to the listener. A
 * listener declared as {@code List<SensorReadingEvent>} cannot tell that null
 * apart from anything else, and will simply throw a NullPointerException deep
 * in the write path — which the error handler then treats as a generic
 * failure and retries forever, wedging the partition. Catching it here and
 * reporting the offending index is what makes the dead-letter path work.
 */
@Component
@ConditionalOnProperty(prefix = "demo.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SensorReadingBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingBatchConsumer.class);
    private static final LogAccessor HEADER_LOG = new LogAccessor(SensorReadingBatchConsumer.class);

    private final CopyBatchWriter copyBatchWriter;

    public SensorReadingBatchConsumer(CopyBatchWriter copyBatchWriter) {
        this.copyBatchWriter = copyBatchWriter;
    }

    @KafkaListener(topics = "${demo.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onBatch(List<ConsumerRecord<String, SensorReadingEvent>> records) {
        if (records.isEmpty()) {
            return;
        }
        List<SensorReadingEvent> events = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            SensorReadingEvent event = records.get(i).value();
            if (event == null) {
                // BatchListenerFailedException carries the INDEX of the bad
                // record. That is what lets DefaultErrorHandler commit
                // everything before it, dead-letter exactly this one record,
                // and redeliver the rest — instead of discarding the whole
                // batch or retrying it forever.
                //
                // The original DeserializationException rides along as the
                // cause so the failure reason reaches the dead-letter headers
                // rather than just "value was null".
                DeserializationException cause = SerializationUtils.getExceptionFromHeader(
                        records.get(i), SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, HEADER_LOG);
                throw new BatchListenerFailedException(
                        "record could not be deserialized; routing to dead-letter topic", cause, i);
            }
            events.add(event);
        }
        long written = copyBatchWriter.write(events);
        if (written != events.size()) {
            log.warn("batch size {} but COPY reported {} rows", events.size(), written);
        }
    }
}
