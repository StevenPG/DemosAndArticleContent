package com.stevenpg.fleet.domain.inspection;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InspectionSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final InspectionRepository repository;

    public InspectionSeeder(InspectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "inspection";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Inspection> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Inspection entity = new Inspection();
            entity.setCode("CHK-%04d".formatted(i + 20 * 1000));
            entity.setName("Inspection unit %d".formatted(i));
            entity.setStatus(InspectionStatus.values()[i % InspectionStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setChecklistRef("CHK%05d".formatted(i * 7 + 20));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
