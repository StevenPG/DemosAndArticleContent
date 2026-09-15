package com.stevenpg.fleet.domain.groundstation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroundStationRepository extends JpaRepository<GroundStation, Long> {

    Optional<GroundStation> findByCode(String code);

    Page<GroundStation> findByStatus(GroundStationStatus status, Pageable pageable);

    List<GroundStation> findByRegionAndActiveTrue(String region);

    List<GroundStationSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(GroundStationStatus status);

    @Query("select avg(e.massKg) from GroundStation e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from GroundStation e")
    long totalCycles();
}
