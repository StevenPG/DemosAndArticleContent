package com.stevenpg.fleet.domain.fuel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FuelUpliftRepository extends JpaRepository<FuelUplift, Long> {

    Optional<FuelUplift> findByCode(String code);

    Page<FuelUplift> findByStatus(FuelUpliftStatus status, Pageable pageable);

    List<FuelUplift> findByRegionAndActiveTrue(String region);

    List<FuelUpliftSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(FuelUpliftStatus status);

    @Query("select avg(e.massKg) from FuelUplift e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from FuelUplift e")
    long totalCycles();
}
