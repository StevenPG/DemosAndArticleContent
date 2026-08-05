package com.example.partitionswap.baseline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The comparison baseline: the same Kafka events, written to a single ordinary
 * table through Spring Data JPA {@code saveAll()} instead of COPY into staging
 * partitions.
 *
 * <p>Run it with either profile:
 * <pre>
 *   ./gradlew :jpa-baseline-service:bootRun --args='--spring.profiles.active=naive'
 *   ./gradlew :jpa-baseline-service:bootRun --args='--spring.profiles.active=tuned'
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
public class JpaBaselineApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpaBaselineApplication.class, args);
    }
}
