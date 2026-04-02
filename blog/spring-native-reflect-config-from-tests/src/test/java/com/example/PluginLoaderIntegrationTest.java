package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest loads the full application context — not a slice.
// This is important: we want all Spring beans (web layer, JDBC, custom services)
// to fully initialize against a real database so the tracing agent sees all the
// reflection that happens during startup.
//
// A unit test would miss the Class.forName() calls we care about — those only
// happen when the test actually calls pluginLoader.invoke().
@SpringBootTest
@Testcontainers
class PluginLoaderIntegrationTest {

    // Testcontainers spins up a real PostgreSQL container for this test class.
    // @ServiceConnection (Spring Boot 4) wires the container's JDBC URL, username,
    // and password into the application context automatically — no @DynamicPropertySource
    // boilerplate needed. Spring Boot knows to start the container before creating
    // the context, so Spring JDBC connects here rather than looking for an embedded DB.
    //
    // Why not H2? Because H2 doesn't exercise the PostgreSQL JDBC driver
    // or any Postgres-specific code paths. If those are missing from the metadata,
    // you'll get a surprise in production.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PluginLoader pluginLoader;

    @Autowired
    PluginRepository pluginRepository;

    @BeforeEach
    void setup() {
        // Seed the database before each test via real JDBC writes.
        // This exercises the full JDBC write path and ensures the plugins
        // are available for the invoke() calls below.
        pluginRepository.deleteAll();
        pluginRepository.save(new PluginRegistration("hello",   "com.example.HelloPlugin"));
        pluginRepository.save(new PluginRegistration("goodbye", "com.example.GoodbyePlugin"));
    }

    @Test
    void helloPluginLoadsAndRuns() throws Exception {
        // This call exercises Class.forName("com.example.HelloPlugin"),
        // getDeclaredConstructor(), newInstance(), and run().
        // The tracing agent observes all of these and writes them to reachability-metadata.json.
        String result = pluginLoader.invoke("com.example.HelloPlugin");
        assertThat(result).isEqualTo("Hello from HelloPlugin");
    }

    @Test
    void goodbyePluginLoadsAndRuns() throws Exception {
        String result = pluginLoader.invoke("com.example.GoodbyePlugin");
        assertThat(result).isEqualTo("Goodbye from GoodbyePlugin");
    }

    @Test
    void pluginRepository_savesAndLoads() {
        // Exercises the full JDBC save + load round-trip.
        // The row mapper lambda in PluginRepository calls the PluginRegistration
        // constructor directly, so the agent captures the constructor usage here.
        var found = pluginRepository.findAll();
        assertThat(found).hasSize(2);
        assertThat(found).extracting(PluginRegistration::getName)
                .containsExactlyInAnyOrder("hello", "goodbye");
    }

    // --- Iteration 2: added after WavePlugin is created ---
    // Uncomment this test and re-run -Pagent test + metadataCopy to add WavePlugin to reachability-metadata.json.

//     @Test
//     void wavePluginLoadsAndRuns() throws Exception {
//         pluginRepository.save(new PluginRegistration("wave", "com.example.WavePlugin"));
//         String result = pluginLoader.invoke("com.example.WavePlugin");
//         assertThat(result).isEqualTo("Wave from WavePlugin");
//     }
}
