package com.stevenpg.fleet.domain.inspection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    Optional<Inspection> findByCode(String code);

    Page<Inspection> findByStatus(InspectionStatus status, Pageable pageable);

    List<Inspection> findByRegionAndActiveTrue(String region);

    List<InspectionSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(InspectionStatus status);

    @Query("select avg(e.massKg) from Inspection e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Inspection e")
    long totalCycles();
}
