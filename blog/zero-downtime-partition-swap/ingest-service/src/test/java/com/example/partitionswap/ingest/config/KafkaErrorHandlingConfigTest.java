package com.example.partitionswap.ingest.config;

import com.example.partitionswap.ingest.copy.PoisonBatchException;
import com.example.partitionswap.ingest.copy.TransientIngestException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the retry-versus-dead-letter policy itself.
 *
 * <p>This is the test for the subtler of the two bugs found while building
 * this: the backoff is deliberately unlimited so that a database outage never
 * discards data, but that same unlimited backoff will happily swallow a poison
 * record too — retrying it forever so it never reaches the recoverer, leaving
 * the partition just as wedged as with no error handling at all. The
 * exceptions that identify un-processable data must therefore be explicitly
 * non-retryable, and nothing about the code makes that obvious.
 *
 * <p>{@code removeClassification} returns the previous classification, which
 * lets the policy be asserted directly rather than through reflection.
 * FALSE means "not retryable" — i.e. hand it straight to the recoverer.
 */
class KafkaErrorHandlingConfigTest {

    private static DefaultErrorHandler handler() {
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        return new KafkaErrorHandlingConfig().kafkaErrorHandler(producerFactory);
    }

    @Test
    void poisonBatchesAreDeadLetteredWithoutRetrying() {
        assertThat(handler().removeClassification(PoisonBatchException.class))
                .as("PoisonBatchException must not be retried; it fails identically every time")
                .isFalse();
    }

    @Test
    void identifiedBadRecordsAreDeadLetteredWithoutRetrying() {
        assertThat(handler().removeClassification(BatchListenerFailedException.class))
                .as("the listener only throws this to name an un-processable record")
                .isFalse();
    }

    @Test
    void deserializationFailuresAreDeadLetteredWithoutRetrying() {
        assertThat(handler().removeClassification(DeserializationException.class))
                .as("framework default, asserted here so a future config change cannot silently drop it")
                .isFalse();
    }

    @Test
    void transientFailuresStayRetryableForever() {
        // The whole point of the policy: the database being unavailable must
        // never cost data. A null classification means "not explicitly
        // classified", which falls through to retryable.
        Boolean classification = handler().removeClassification(TransientIngestException.class);
        assertThat(classification == null || classification)
                .as("TransientIngestException must remain retryable")
                .isTrue();
    }

    @Test
    void retriesAreNotBoundedByAnElapsedTimeLimit() {
        // An ExponentialBackOff with a maxElapsedTime would eventually hand a
        // transient failure to the dead-letter recoverer, turning a database
        // outage into data loss. Verified by asserting the backoff never
        // returns STOP, even far beyond any plausible outage.
        var execution = KafkaErrorHandlingConfig.retryBackOff().start();
        long total = 0;
        for (int i = 0; i < 10_000; i++) {
            long next = execution.nextBackOff();
            assertThat(next).as("backoff must never signal STOP").isNotEqualTo(-1L);
            total += next;
        }
        assertThat(total).as("10k retries should span days, not minutes")
                .isGreaterThan(java.time.Duration.ofHours(24).toMillis());
    }
}
