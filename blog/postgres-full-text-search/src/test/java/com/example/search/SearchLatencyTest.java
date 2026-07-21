package com.example.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs each search strategy against the 50k seeded articles and prints p50/p99
 * latency. Flyway seeds the data on context startup.
 *
 * Run with: ./gradlew test --tests SearchLatencyTest
 */
@SpringBootTest
@Testcontainers
class SearchLatencyTest {

    static final int WARMUP = 50;
    static final int ITERATIONS = 500;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    SearchRepository repository;

    @Test
    void compareLatency() {
        // Sanity: every strategy actually finds results
        assertThat(repository.fullText("postgres indexing")).isNotEmpty();
        assertThat(repository.fuzzy("Postgress performence")).isNotEmpty(); // typos!
        assertThat(repository.ilike("indexing")).isNotEmpty();

        System.out.println("\n=== Search latency, 50k rows ===");
        report("ilike    ", () -> repository.ilike("indexing"));
        report("fulltext ", () -> repository.fullText("postgres indexing"));
        report("trigram  ", () -> repository.fuzzy("Postgress performence"));
    }

    private void report(String name, Supplier<List<SearchResult>> search) {
        for (int i = 0; i < WARMUP; i++) {
            search.get();
        }
        long[] samples = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            search.get();
            samples[i] = (System.nanoTime() - start) / 1_000;
        }
        java.util.Arrays.sort(samples);
        System.out.printf("%s p50=%,7dµs  p99=%,7dµs%n",
                name, samples[ITERATIONS / 2], samples[(int) (ITERATIONS * 0.99)]);
    }
}
