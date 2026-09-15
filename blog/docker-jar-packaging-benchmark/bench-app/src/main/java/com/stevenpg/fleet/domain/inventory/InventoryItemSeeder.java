package com.stevenpg.fleet.domain.inventory;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryItemSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final InventoryItemRepository repository;

    public InventoryItemSeeder(InventoryItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "inventory";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<InventoryItem> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            InventoryItem entity = new InventoryItem();
            entity.setCode("BIN-%04d".formatted(i + 16 * 1000));
            entity.setName("InventoryItem unit %d".formatted(i));
            entity.setStatus(InventoryItemStatus.values()[i % InventoryItemStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setBinLocation("BIN%05d".formatted(i * 7 + 16));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
