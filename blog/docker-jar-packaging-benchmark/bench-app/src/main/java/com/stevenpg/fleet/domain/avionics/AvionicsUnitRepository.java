package com.stevenpg.fleet.domain.avionics;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AvionicsUnitRepository extends JpaRepository<AvionicsUnit, Long> {

    Optional<AvionicsUnit> findByCode(String code);

    Page<AvionicsUnit> findByStatus(AvionicsUnitStatus status, Pageable pageable);

    List<AvionicsUnit> findByRegionAndActiveTrue(String region);

    List<AvionicsUnitSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(AvionicsUnitStatus status);

    @Query("select avg(e.massKg) from AvionicsUnit e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from AvionicsUnit e")
    long totalCycles();
}
