package com.example.partitionswap.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo knobs. The defaults produce ~60 messages per minute so each per-minute
 * partition holds a readable number of rows; crank {@code messagesPerSecond}
 * up (the write path is a single COPY per consumed batch, so thousands per
 * second is fine) to see the batching and COPY throughput logging earn its keep.
 */
/**
 * @param topicPartitions partitions to create the topic with. This is the hard
 *                        ceiling on consumer parallelism — listener
 *                        concurrency above this number just creates idle
 *                        threads — so it has to move in lockstep with
 *                        {@code spring.kafka.listener.concurrency} when
 *                        scaling onto a bigger machine.
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(String topic, int topicPartitions, Consumer consumer, Producer producer) {

    /**
     * @param totalMessages stop after emitting this many (0 = run forever).
     *                      Used to preload a fixed, reproducible message set
     *                      for the benchmark, so no run is timed against a
     *                      live producer whose rate could drift.
     * @param threads       parallelism for preload mode only. The rate-limited
     *                      demo mode is single-threaded by design; preloading
     *                      tens of millions of messages is not something one
     *                      thread should be asked to do.
     */
    public record Producer(boolean enabled, int messagesPerSecond, int deviceCount,
                           long totalMessages, int threads) {
    }

    /** Lets the app run as a pure producer while preloading the topic. */
    public record Consumer(boolean enabled) {
    }
}
