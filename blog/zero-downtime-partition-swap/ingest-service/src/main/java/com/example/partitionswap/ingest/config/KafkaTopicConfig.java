package com.example.partitionswap.ingest.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Six partitions so the consumer side can scale horizontally: each ingest
     * instance COPYs its own batches independently, and because partitions are
     * created per arrival-minute (not per consumer), all instances feed the
     * same staging table without coordination.
     */
    @Bean
    public NewTopic sensorReadingsTopic(DemoProperties props) {
        return TopicBuilder.name(props.topic())
                .partitions(6)
                .replicas(1)
                .build();
    }
}
