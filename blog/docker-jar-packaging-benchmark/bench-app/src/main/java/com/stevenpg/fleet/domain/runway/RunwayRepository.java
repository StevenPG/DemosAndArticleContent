package com.stevenpg.fleet.domain.runway;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RunwayRepository extends JpaRepository<Runway, Long> {

    Optional<Runway> findByCode(String code);

    Page<Runway> findByStatus(RunwayStatus status, Pageable pageable);

    List<Runway> findByRegionAndActiveTrue(String region);

    List<RunwaySummary> findByActiveTrue(Pageable pageable);

    long countByStatus(RunwayStatus status);

    @Query("select avg(e.massKg) from Runway e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Runway e")
    long totalCycles();
}
