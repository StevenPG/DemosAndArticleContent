package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed, validated view of the app.downstream.* configuration. Bound once at
 * startup by @ConfigurationPropertiesScan; the annotation processor also
 * generates IDE metadata for these keys.
 */
@ConfigurationProperties(prefix = "app.downstream")
public record DownstreamProperties(Endpoint payment, Endpoint inventory) {

    public record Endpoint(String baseUrl) {
    }
}
