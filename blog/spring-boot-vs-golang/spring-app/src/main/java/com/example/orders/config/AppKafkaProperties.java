package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(String ordersTopic) {
}
