package com.stevenpg.jackson3example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The "AFTER" application: Spring Boot 4.1, Jackson 3.1 (tools.jackson).
 * Runs on port 8083 - see application.yml. Compare every class here with
 * its identically-named sibling in jackson2-example.
 */
@SpringBootApplication
public class Jackson3ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Jackson3ExampleApplication.class, args);
    }
}
