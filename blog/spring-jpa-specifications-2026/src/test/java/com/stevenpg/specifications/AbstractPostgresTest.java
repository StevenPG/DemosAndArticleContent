package com.stevenpg.specifications;

import com.stevenpg.specifications.config.SampleData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stevenpg.specifications.repository.FlightRepository;

/**
 * One real PostgreSQL container for the whole suite.
 * <p>
 * The container is started from a static initialiser and never stopped - the singleton container
 * pattern. The obvious alternative, {@code @Testcontainers} on this class plus {@code @Container}
 * on the field, does not survive a suite: the JUnit extension starts the field in
 * {@code beforeAll} and stops it in {@code afterAll} of <em>every</em> subclass, while Spring
 * caches one application context across all of them. The second test class then gets a fresh
 * container on a fresh random port while the cached {@code DataSource} still points at the dead
 * one, and every test after the first class fails with
 * {@code SQLTransientConnectionException: connection is not available}. Testcontainers' Ryuk
 * sidecar removes the container when the JVM exits, so nothing leaks.
 * <p>
 * {@code @ServiceConnection} wires the JDBC URL, username and password into the context with no
 * {@code @DynamicPropertySource} boilerplate.
 * <p>
 * Note the import: Testcontainers 2.x moved {@code PostgreSQLContainer} out of
 * {@code org.testcontainers.containers} and into {@code org.testcontainers.postgresql}, and
 * dropped the self-referential generic that used to force
 * {@code new PostgreSQLContainer<>("postgres:17")}.
 * <p>
 * The {@code test} profile switches off {@code SampleDataLoader}, the {@code CommandLineRunner}
 * that seeds the demo app on startup. Each test seeds itself in {@link #seed()} inside the
 * rolled-back transaction, so letting the runner commit its own 120 flights first would double
 * every count in the suite.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractPostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected FlightRepository flights;

    /**
     * Each test starts from the same 120 flights. {@code @Transactional} on the class rolls the
     * whole thing back afterwards, so tests cannot see each other's writes.
     */
    @BeforeEach
    void seed() {
        SampleData.load(entityManager);
    }

    /**
     * Forces pending changes to the database and empties the persistence context, so the next
     * query really hits PostgreSQL instead of being served from the first-level cache. Bulk
     * update and delete tests need this to prove anything.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
