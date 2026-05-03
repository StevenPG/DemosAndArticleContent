package com.example.batchguide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot 3.5 / Spring Batch 5.x Order Import application.
 *
 * <p>The application deliberately disables automatic job execution on startup
 * ({@code spring.batch.job.enabled=false}) so that jobs are triggered explicitly
 * — either programmatically or via a REST endpoint / CLI runner in production.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
