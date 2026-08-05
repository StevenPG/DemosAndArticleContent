package com.example.partitionswap.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo knobs. The defaults produce ~60 messages per minute so each per-minute
 * partition holds a readable number of rows; crank {@code messagesPerSecond}
 * up (the write path is a single COPY per consumed batch, so thousands per
 * second is fine) to see the batching and COPY throughput logging earn its keep.
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(String topic, Consumer consumer, Producer producer) {

    /**
     * @param totalMessages stop after emitting this many (0 = run forever).
     *                      Used to preload a fixed, reproducible message set
     *                      for the benchmark, so no run is timed against a
     *                      live producer whose rate could drift.
     */
    public record Producer(boolean enabled, int messagesPerSecond, int deviceCount, long totalMessages) {
    }

    /** Lets the app run as a pure producer while preloading the topic. */
    public record Consumer(boolean enabled) {
    }
}
