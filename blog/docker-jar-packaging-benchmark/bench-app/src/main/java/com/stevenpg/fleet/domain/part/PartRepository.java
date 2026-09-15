package com.stevenpg.fleet.domain.part;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartRepository extends JpaRepository<Part, Long> {

    Optional<Part> findByCode(String code);

    Page<Part> findByStatus(PartStatus status, Pageable pageable);

    List<Part> findByRegionAndActiveTrue(String region);

    List<PartSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(PartStatus status);

    @Query("select avg(e.massKg) from Part e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Part e")
    long totalCycles();
}
