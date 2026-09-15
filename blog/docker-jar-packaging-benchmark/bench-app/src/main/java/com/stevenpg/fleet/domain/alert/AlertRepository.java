package com.stevenpg.fleet.domain.alert;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByCode(String code);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    List<Alert> findByRegionAndActiveTrue(String region);

    List<AlertSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(AlertStatus status);

    @Query("select avg(e.massKg) from Alert e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Alert e")
    long totalCycles();
}
