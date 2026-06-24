package com.example.actuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Ultimate Spring Boot 3 Actuator demo.
 *
 * <p>This single application is wired up to exercise as many Spring Boot Actuator
 * capabilities as possible:
 * <ul>
 *     <li>{@code @EnableScheduling} &rarr; populates the {@code scheduledtasks} endpoint.</li>
 *     <li>{@code @EnableCaching} &rarr; populates the {@code caches} endpoint and cache metrics.</li>
 *     <li>{@link BufferingApplicationStartup} &rarr; enables the {@code startup} endpoint, which
 *         reports detailed application start-up timings.</li>
 * </ul>
 *
 * <p>Every actuator feature lives in a clearly-named, dedicated class so that the
 * accompanying blog post can point at exactly one place per concept.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
@ConfigurationPropertiesScan
public class UltimateActuatorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(UltimateActuatorApplication.class);
        // The startup endpoint needs a BufferingApplicationStartup to capture the
        // start-up step timings. 2048 is the max number of events to retain.
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
    }
}
