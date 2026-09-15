package com.stevenpg.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fleet telemetry API - the benchmark subject.
 *
 * <p>Deliberately large: ~40 generated domain packages on top of MVC, JPA,
 * validation, security and actuator. The packaging experiments in ../docker all
 * ship this exact jar, so any difference between them comes from how it is
 * packaged and started, never from the application itself.
 */
@SpringBootApplication
public class FleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetApplication.class, args);
    }
}
