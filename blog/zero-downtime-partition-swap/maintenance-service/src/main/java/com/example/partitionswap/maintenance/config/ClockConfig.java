package com.example.partitionswap.maintenance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected instead of Instant.now() so tests can move time deterministically. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
