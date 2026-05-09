package com.stevenpg.ecommerce.inventory.internal;

import com.stevenpg.ecommerce.inventory.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductId(UUID productId);
}
