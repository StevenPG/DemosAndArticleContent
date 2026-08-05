package com.example.partitionswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zero-downtime partition swap demo.
 *
 * <p>Three moving parts, all driven by the scheduler:
 * <ul>
 *   <li>{@link com.example.partitionswap.producer.EventProducer} writes continuously into
 *       {@code events_ingest}, a per-minute range-partitioned table with only its primary
 *       key index on the write path.</li>
 *   <li>{@link com.example.partitionswap.partition.PartitionSwapScheduler} runs once a
 *       minute: it detaches the just-closed minute partition from the ingest table
 *       ({@code DETACH PARTITION ... CONCURRENTLY}), builds the read-optimized indexes on
 *       the now-standalone table, and attaches it to the {@code events} read table — all
 *       without blocking the producer or the consumer.</li>
 *   <li>{@link com.example.partitionswap.consumer.EventConsumer} queries the indexed
 *       {@code events} read table through Spring Data JPA.</li>
 * </ul>
 */
@EnableScheduling
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
