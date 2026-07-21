package com.example.tc;

import org.springframework.boot.SpringApplication;

/**
 * Local development with real dependencies and zero installed infrastructure:
 *
 *   ./gradlew bootTestRun
 *
 * Boots the app with Postgres and Kafka running in containers, wired up
 * automatically via the same @ServiceConnection config the tests use.
 */
public class TestTcDemoApplication {

    public static void main(String[] args) {
        SpringApplication.from(TcDemoApplication::main)
                .with(ContainersConfig.class)
                .run(args);
    }
}
