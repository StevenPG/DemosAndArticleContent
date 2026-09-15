package com.stevenpg.fleet.domain.certification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    Optional<Certification> findByCode(String code);

    Page<Certification> findByStatus(CertificationStatus status, Pageable pageable);

    List<Certification> findByRegionAndActiveTrue(String region);

    List<CertificationSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(CertificationStatus status);

    @Query("select avg(e.massKg) from Certification e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Certification e")
    long totalCycles();
}
