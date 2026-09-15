package com.stevenpg.fleet.domain.workorder;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByCode(String code);

    Page<WorkOrder> findByStatus(WorkOrderStatus status, Pageable pageable);

    List<WorkOrder> findByRegionAndActiveTrue(String region);

    List<WorkOrderSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(WorkOrderStatus status);

    @Query("select avg(e.massKg) from WorkOrder e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from WorkOrder e")
    long totalCycles();
}
