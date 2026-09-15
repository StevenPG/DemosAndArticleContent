package com.stevenpg.fleet.domain.workorder;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkOrderSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final WorkOrderRepository repository;

    public WorkOrderSeeder(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "workorder";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<WorkOrder> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            WorkOrder entity = new WorkOrder();
            entity.setCode("WO-%04d".formatted(i + 7 * 1000));
            entity.setName("WorkOrder unit %d".formatted(i));
            entity.setStatus(WorkOrderStatus.values()[i % WorkOrderStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setOrderNumber("WO%05d".formatted(i * 7 + 7));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
