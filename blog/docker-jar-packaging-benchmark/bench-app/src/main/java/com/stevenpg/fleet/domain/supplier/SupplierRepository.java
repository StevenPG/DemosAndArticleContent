package com.stevenpg.fleet.domain.supplier;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCode(String code);

    Page<Supplier> findByStatus(SupplierStatus status, Pageable pageable);

    List<Supplier> findByRegionAndActiveTrue(String region);

    List<SupplierSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(SupplierStatus status);

    @Query("select avg(e.massKg) from Supplier e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from Supplier e")
    long totalCycles();
}
