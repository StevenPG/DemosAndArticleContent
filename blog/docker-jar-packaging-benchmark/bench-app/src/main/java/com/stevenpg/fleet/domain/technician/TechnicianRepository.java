package com.stevenpg.fleet.domain.technician;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    Optional<Technician> findByCode(String code);

    Page<Technician> findByStatus(TechnicianStatus status, Pageable pageable);

    List<Technician> findByRegionAndActiveTrue(String region);

    List<TechnicianSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(TechnicianStatus status);

    @Query("select avg(e.massKg) from Technician e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Technician e")
    long totalCycles();
}
