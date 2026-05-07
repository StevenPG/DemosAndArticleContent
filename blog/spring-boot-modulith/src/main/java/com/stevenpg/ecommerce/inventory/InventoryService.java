package com.stevenpg.ecommerce.inventory;

import com.stevenpg.ecommerce.inventory.internal.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryItemRepository repository;

    InventoryService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    public List<InventoryItem> findAll() {
        return repository.findAll();
    }

    public Optional<InventoryItem> findByProductId(UUID productId) {
        return repository.findByProductId(productId);
    }

    @Transactional
    public InventoryItem addStock(UUID productId, int quantity) {
        InventoryItem item = repository.findByProductId(productId)
                .orElseGet(() -> repository.save(new InventoryItem(productId, 0)));
        item.addStock(quantity);
        return repository.save(item);
    }
}
