package com.stevenpg.specifications;

import com.stevenpg.specifications.config.SampleData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stevenpg.specifications.repository.FlightRepository;

/**
 * One real PostgreSQL container for the whole suite.
 * <p>
 * The container is {@code static}, so Testcontainers starts it once and reuses it across every
 * test class. {@code @ServiceConnection} wires the JDBC URL, username and password into the
 * context with no {@code @DynamicPropertySource} boilerplate.
 * <p>
 * Note the import: Testcontainers 2.x moved {@code PostgreSQLContainer} out of
 * {@code org.testcontainers.containers} and into {@code org.testcontainers.postgresql}, and
 * dropped the self-referential generic that used to force
 * {@code new PostgreSQLContainer<>("postgres:17")}.
 */
@SpringBootTest
@Testcontainers
@Transactional
public abstract class AbstractPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

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
