package com.stevenpg.fleet.domain.maintenance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {

    Optional<MaintenanceTask> findByCode(String code);

    Page<MaintenanceTask> findByStatus(MaintenanceTaskStatus status, Pageable pageable);

    List<MaintenanceTask> findByRegionAndActiveTrue(String region);

    List<MaintenanceTaskSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(MaintenanceTaskStatus status);

    @Query("select avg(e.massKg) from MaintenanceTask e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from MaintenanceTask e")
    long totalCycles();
}
