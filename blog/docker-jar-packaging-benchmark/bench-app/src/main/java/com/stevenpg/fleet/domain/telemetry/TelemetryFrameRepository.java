package com.stevenpg.fleet.domain.telemetry;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TelemetryFrameRepository extends JpaRepository<TelemetryFrame, Long> {

    Optional<TelemetryFrame> findByCode(String code);

    Page<TelemetryFrame> findByStatus(TelemetryFrameStatus status, Pageable pageable);

    List<TelemetryFrame> findByRegionAndActiveTrue(String region);

    List<TelemetryFrameSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(TelemetryFrameStatus status);

    @Query("select avg(e.massKg) from TelemetryFrame e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from TelemetryFrame e")
    long totalCycles();
}
