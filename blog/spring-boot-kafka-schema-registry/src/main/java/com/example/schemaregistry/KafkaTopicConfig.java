package com.example.schemaregistry;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopicConfig {

    /**
     * Create the orders topic on startup so the demo works against a fresh broker.
     * In production you would usually manage topics outside the app.
     */
    @Bean
    NewTopic ordersTopic(@Value("${app.topic}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
