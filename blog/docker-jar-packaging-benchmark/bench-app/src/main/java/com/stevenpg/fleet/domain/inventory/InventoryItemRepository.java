package com.stevenpg.fleet.domain.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByCode(String code);

    Page<InventoryItem> findByStatus(InventoryItemStatus status, Pageable pageable);

    List<InventoryItem> findByRegionAndActiveTrue(String region);

    List<InventoryItemSummary> findByActiveTrue(Pageable pageable);

    long countByStatus(InventoryItemStatus status);

    @Query("select avg(e.massKg) from InventoryItem e where e.active = true")
    Double averageMassKg();

    @Query("select coalesce(sum(e.cycles), 0) from InventoryItem e")
    long totalCycles();
}
