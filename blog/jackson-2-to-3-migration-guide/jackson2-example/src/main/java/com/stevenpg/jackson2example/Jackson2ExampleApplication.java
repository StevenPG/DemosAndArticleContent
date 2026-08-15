package com.stevenpg.jackson2example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The "BEFORE" application: Spring Boot 3.5, Jackson 2.21 (com.fasterxml).
 * Runs on port 8082 - see application.yml. Compare every class here with
 * its identically-named sibling in jackson3-example.
 */
@SpringBootApplication
public class Jackson2ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Jackson2ExampleApplication.class, args);
    }
}
