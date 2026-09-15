package com.stevenpg.fleet.domain.airport;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    Optional<Airport> findByCode(String code);

    Page<Airport> findByStatus(AirportStatus status, Pageable pageable);

    List<Airport> findByRegionAndActiveTrue(String region);

    List<AirportSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(AirportStatus status);

    @Query("select avg(e.massKg) from Airport e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Airport e")
    long totalCycles();
}
