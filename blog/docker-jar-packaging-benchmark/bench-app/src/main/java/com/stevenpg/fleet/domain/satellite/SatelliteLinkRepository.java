package com.stevenpg.fleet.domain.satellite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SatelliteLinkRepository extends JpaRepository<SatelliteLink, Long> {

    Optional<SatelliteLink> findByCode(String code);

    Page<SatelliteLink> findByStatus(SatelliteLinkStatus status, Pageable pageable);

    List<SatelliteLink> findByRegionAndActiveTrue(String region);

    List<SatelliteLinkSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(SatelliteLinkStatus status);

    @Query("select avg(e.massKg) from SatelliteLink e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from SatelliteLink e")
    long totalCycles();
}
