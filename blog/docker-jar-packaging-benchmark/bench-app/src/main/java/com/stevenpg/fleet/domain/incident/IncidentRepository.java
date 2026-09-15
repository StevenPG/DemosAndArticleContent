package com.stevenpg.fleet.domain.incident;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByCode(String code);

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    List<Incident> findByRegionAndActiveTrue(String region);

    List<IncidentSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(IncidentStatus status);

    @Query("select avg(e.massKg) from Incident e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Incident e")
    long totalCycles();
}
