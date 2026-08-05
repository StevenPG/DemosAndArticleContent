package com.example.partitionswap.baseline.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * A completely ordinary Spring Data repository. {@code saveAll} comes from
 * {@link JpaRepository} — no custom implementation, no bulk-insert trickery.
 * That is the point: this is the code a team writes without thinking about it.
 */
public interface SensorReadingRowRepository extends JpaRepository<SensorReadingRow, UUID> {

    /**
     * Retention, baseline style. A partitioned table drops a whole partition;
     * a flat table has to find and delete rows, marking each one dead in the
     * heap and in every index, leaving the space to be reclaimed by a later
     * VACUUM. Written as native SQL so the cost is Postgres's, not
     * Hibernate's.
     */
    @Query(value = "DELETE FROM sensor_readings_jpa WHERE ingested_at < :cutoff", nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
