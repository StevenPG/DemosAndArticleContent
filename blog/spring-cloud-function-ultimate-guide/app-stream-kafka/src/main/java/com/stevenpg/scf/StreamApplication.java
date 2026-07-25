package com.stevenpg.scf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Kafka surface.
 *
 * <p>Like the web app, this class has no messaging code. Spring Cloud Stream
 * reads {@code spring.cloud.function.definition} + the binding config in
 * {@code application.yml} and connects the {@code orderPipeline} function to the
 * {@code orders} (in) and {@code decisions} (out) topics. Poison messages are
 * diverted to a dead-letter topic and every invocation is traced.
 */
@SpringBootApplication
public class StreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamApplication.class, args);
    }
}
