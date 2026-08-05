package com.example.partitionswap.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo knobs. The defaults produce ~60 messages per minute so each per-minute
 * partition holds a readable number of rows; crank {@code messagesPerSecond}
 * up (the write path is a single COPY per consumed batch, so thousands per
 * second is fine) to see the batching and COPY throughput logging earn its keep.
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(String topic, Producer producer) {

    public record Producer(boolean enabled, int messagesPerSecond, int deviceCount) {
    }
}
