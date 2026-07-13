package com.example.bench;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/**
 * Active only under the "training" profile (train.sh sets it).
 *
 * An AOT training run should look like real startup plus early traffic:
 * this runner exercises the readiness probe and the hot REST paths through
 * the real HTTP stack (not MockMvc - we want the connector, codec, and
 * Jackson object graphs materialized), then exits cleanly so the JVM can
 * write the AOT cache configuration on shutdown.
 */
@Configuration
@Profile("training")
public class TrainingRunner {

    @Bean
    @Order(Integer.MAX_VALUE) // run after DataLoader has seeded
    CommandLineRunner train(ConfigurableApplicationContext context) {
        return args -> {
            var client = HttpClient.newHttpClient();
            for (String path : new String[] {
                    "/actuator/health/readiness",
                    "/products",
                    "/products/1"
            }) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080" + path))
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("[training] GET " + path + " -> " + response.statusCode());
            }
            // Clean exit; the JVM writes the AOT cache during shutdown.
            System.exit(SpringApplication.exit(context, () -> 0));
        };
    }
}
