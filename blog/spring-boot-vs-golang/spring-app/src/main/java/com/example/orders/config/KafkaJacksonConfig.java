package com.example.orders.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * The auto-configured Kafka JsonSerializer/JsonDeserializer build their own
 * plain ObjectMapper rather than reusing Spring's Jackson auto-configuration,
 * so java.time types like Instant (used in OrderEvent#occurredAt) fail to
 * serialize unless JavaTimeModule is registered here explicitly. Timestamps
 * must also be written as ISO-8601 strings, not the JavaTimeModule default of
 * numeric epoch timestamps, since the Go consumer unmarshals occurredAt into
 * a time.Time, which only accepts RFC3339 strings.
 */
@Configuration
public class KafkaJacksonConfig {

    @Bean
    public DefaultKafkaProducerFactoryCustomizer kafkaProducerJavaTimeCustomizer() {
        ObjectMapper objectMapper = kafkaObjectMapper();
        return producerFactory -> producerFactory.setValueSerializer(new JsonSerializer<>(objectMapper));
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer kafkaConsumerJavaTimeCustomizer() {
        ObjectMapper objectMapper = kafkaObjectMapper();
        return consumerFactory -> consumerFactory.setValueDeserializer(new JsonDeserializer<>(Object.class, objectMapper));
    }

    private static ObjectMapper kafkaObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
