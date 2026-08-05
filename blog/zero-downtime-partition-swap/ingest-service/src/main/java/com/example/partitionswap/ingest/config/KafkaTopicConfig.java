package com.example.partitionswap.ingest.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Partition count is the hard ceiling on consumer parallelism: each ingest
     * thread owns whole partitions and COPYs its own batches independently, so
     * six partitions means at most six concurrent COPY streams no matter how
     * many cores the machine has.
     *
     * <p>Configurable via {@code demo.topic-partitions} precisely because it is
     * a ceiling — moving to a bigger machine means raising this and
     * {@code spring.kafka.listener.concurrency} together. Because staging
     * tables are keyed by arrival minute rather than by consumer, every thread
     * and every instance feeds the same table with no coordination, so raising
     * it needs no other changes.
     *
     * <p>Kafka can increase a topic's partition count but never decrease it;
     * on an existing topic this value is applied as an increase only.
     */
    @Bean
    public NewTopic sensorReadingsTopic(DemoProperties props) {
        return TopicBuilder.name(props.topic())
                .partitions(props.topicPartitions())
                .replicas(1)
                .build();
    }
}
