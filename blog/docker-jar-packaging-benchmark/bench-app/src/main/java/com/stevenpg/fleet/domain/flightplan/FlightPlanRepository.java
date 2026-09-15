package com.stevenpg.fleet.domain.flightplan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlightPlanRepository extends JpaRepository<FlightPlan, Long> {

    Optional<FlightPlan> findByCode(String code);

    Page<FlightPlan> findByStatus(FlightPlanStatus status, Pageable pageable);

    List<FlightPlan> findByRegionAndActiveTrue(String region);

    List<FlightPlanSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(FlightPlanStatus status);

    @Query("select avg(e.massKg) from FlightPlan e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from FlightPlan e")
    long totalCycles();
}
