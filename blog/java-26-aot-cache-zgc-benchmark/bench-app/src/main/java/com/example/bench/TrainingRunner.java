package com.example.bench;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

/**
 * Active only under the "training" profile (train.sh sets it).
 *
 * An AOT training run should look like real startup plus early traffic:
 * this runner exercises the readiness probe and the hot REST paths through
 * the real HTTP stack (not MockMvc - we want the connector, codec, and
 * Jackson object graphs materialized), then exits cleanly so the JVM can
 * write the AOT cache configuration on shutdown.
 *
 * The trigger is the readiness state flipping to ACCEPTING_TRAFFIC, not a
 * CommandLineRunner, for two reasons:
 *
 *   1. /actuator/health/readiness answers 503 until that event. A runner
 *      firing earlier trains the probe's failure path and reports
 *      "[training] GET /actuator/health/readiness -> 503".
 *   2. Runners are ordered against each other, and DataLoader's seeding
 *      runner has to win. Both were declared at Integer.MAX_VALUE, which is
 *      a tie, not an ordering - the training traffic could hit /products
 *      before a single row existed, so /products/1 answered 500 and
 *      Hibernate's write path never made it into the cache.
 *
 * Boot publishes ACCEPTING_TRAFFIC after every runner has returned, so by
 * the time this fires the data is in and the probe is green.
 */
@Configuration
@Profile("training")
public class TrainingRunner {

    private final ConfigurableApplicationContext context;

    TrainingRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @EventListener
    void train(AvailabilityChangeEvent<ReadinessState> event) throws Exception {
        if (event.getState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return;
        }
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
    }
}
