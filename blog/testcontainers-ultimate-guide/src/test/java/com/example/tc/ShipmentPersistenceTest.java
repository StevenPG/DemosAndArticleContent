package com.example.tc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres via @ServiceConnection: Flyway runs the real migrations against a
 * real PostgreSQL 18 — the exact database version production runs.
 */
@SpringBootTest
@Import(ContainersConfig.class)
class ShipmentPersistenceTest {

    @Autowired
    ShipmentRepository repository;

    @Test
    void savesAndFindsByTrackingNumber() {
        repository.save(new Shipment("TRACK-123", "CREATED"));

        assertThat(repository.findByTrackingNumber("TRACK-123"))
                .isPresent()
                .get()
                .extracting(Shipment::getStatus)
                .isEqualTo("CREATED");
    }

    @Test
    void uniqueConstraintFromFlywayMigrationIsEnforced() {
        repository.saveAndFlush(new Shipment("TRACK-DUP", "CREATED"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Shipment("TRACK-DUP", "CREATED")));
    }
}
