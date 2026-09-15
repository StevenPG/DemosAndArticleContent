package com.stevenpg.fleet.domain.sensor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findByCode(String code);

    Page<Sensor> findByStatus(SensorStatus status, Pageable pageable);

    List<Sensor> findByRegionAndActiveTrue(String region);

    List<SensorSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(SensorStatus status);

    @Query("select avg(e.massKg) from Sensor e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Sensor e")
    long totalCycles();
}
