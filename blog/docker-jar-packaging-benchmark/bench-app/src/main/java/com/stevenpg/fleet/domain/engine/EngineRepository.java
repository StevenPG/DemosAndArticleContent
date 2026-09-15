package com.stevenpg.fleet.domain.engine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EngineRepository extends JpaRepository<Engine, Long> {

    Optional<Engine> findByCode(String code);

    Page<Engine> findByStatus(EngineStatus status, Pageable pageable);

    List<Engine> findByRegionAndActiveTrue(String region);

    List<EngineSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(EngineStatus status);

    @Query("select avg(e.massKg) from Engine e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Engine e")
    long totalCycles();
}
