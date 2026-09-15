package com.stevenpg.fleet.domain.waypoint;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaypointRepository extends JpaRepository<Waypoint, Long> {

    Optional<Waypoint> findByCode(String code);

    Page<Waypoint> findByStatus(WaypointStatus status, Pageable pageable);

    List<Waypoint> findByRegionAndActiveTrue(String region);

    List<WaypointSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(WaypointStatus status);

    @Query("select avg(e.massKg) from Waypoint e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Waypoint e")
    long totalCycles();
}
