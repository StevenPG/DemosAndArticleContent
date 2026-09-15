package com.stevenpg.fleet.domain.aircraft;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {

    Optional<Aircraft> findByCode(String code);

    Page<Aircraft> findByStatus(AircraftStatus status, Pageable pageable);

    List<Aircraft> findByRegionAndActiveTrue(String region);

    List<AircraftSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(AircraftStatus status);

    @Query("select avg(e.massKg) from Aircraft e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Aircraft e")
    long totalCycles();
}
