package com.example.partitionswap.ingest.config;

import com.example.partitionswap.ingest.copy.PoisonBatchException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * What happens when a batch cannot be written.
 *
 * <p>Without this, an exception from the listener means the offsets are never
 * committed and Kafka redelivers the same batch immediately, forever, as fast
 * as the loop can spin — a single malformed record silently wedges a partition
 * and saturates a CPU. That is the default behaviour, and it is the failure
 * mode most Kafka ingestion demos ship with.
 *
 * <p>The policy here has two halves, and the split matters more than either
 * half:
 * <ul>
 *   <li><b>Retry forever, with backoff, by default.</b> The database being
 *       down is not the batch's fault. An exponential backoff with no elapsed
 *       time limit means a partition stalls loudly during an outage and drains
 *       itself afterwards, losing nothing.</li>
 *   <li><b>Dead-letter immediately for un-processable data.</b> Deserialization
 *       failures and {@link PoisonBatchException} will fail identically on
 *       every attempt, so retrying only blocks the partition. These go
 *       straight to {@code <topic>.DLT} and the consumer moves on.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic sensorReadingsDltTopic(DemoProperties props) {
        return TopicBuilder.name(props.topic() + DLT_SUFFIX)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Dead letters are published as raw bytes, not as {@code SensorReadingEvent}.
     * A record that failed to deserialize has no object form to re-serialize —
     * only the original bytes, which are exactly what an operator needs to see.
     *
     * <p>Deliberately NOT a {@code @Bean}: Boot's Kafka auto-configuration backs
     * off entirely when any {@code KafkaTemplate} bean is present, so exposing
     * this one would remove the application's own template.
     */
    private static KafkaTemplate<byte[], byte[]> deadLetterTemplate(ProducerFactory<?, ?> producerFactory) {
        Map<String, Object> config = new HashMap<>(producerFactory.getConfigurationProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplate(producerFactory),
                (record, exception) -> {
                    log.error("dead-lettering record from {}-{} offset {}: {}",
                            record.topic(), record.partition(), record.offset(), exception.toString());
                    // Single-partition DLT: volume is (or had better be) low,
                    // and ordering across the whole DLT is more useful than
                    // parallelism when a human is reading it.
                    return new TopicPartition(record.topic() + DLT_SUFFIX, 0);
                });

        // No maxElapsedTime: transient failures retry indefinitely rather than
        // discarding good data. The interval caps at 30s so an outage does not
        // turn into an hours-long blind spot after the delay compounds.
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Skip the retries entirely for failures that cannot succeed on a
        // retry. Without this the unlimited backoff above swallows them too:
        // the record is retried forever and never reaches the recoverer, so
        // the partition wedges exactly as it would with no error handling at
        // all — just more slowly.
        //
        // BatchListenerFailedException is on this list because the listener
        // only ever throws it to say "I have identified THIS record as
        // un-processable", which is by definition not worth retrying.
        handler.addNotRetryableExceptions(
                PoisonBatchException.class,
                BatchListenerFailedException.class);
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }
}
