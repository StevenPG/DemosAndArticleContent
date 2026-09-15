package com.stevenpg.fleet.domain.maintenance;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MaintenanceTaskSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final MaintenanceTaskRepository repository;

    public MaintenanceTaskSeeder(MaintenanceTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "maintenance";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<MaintenanceTask> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            MaintenanceTask entity = new MaintenanceTask();
            entity.setCode("AMM-%04d".formatted(i + 6 * 1000));
            entity.setName("MaintenanceTask unit %d".formatted(i));
            entity.setStatus(MaintenanceTaskStatus.values()[i % MaintenanceTaskStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setManualRef("AMM%05d".formatted(i * 7 + 6));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
