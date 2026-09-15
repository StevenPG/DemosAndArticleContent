package com.stevenpg.fleet.domain.avionics;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AvionicsUnitSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final AvionicsUnitRepository repository;

    public AvionicsUnitSeeder(AvionicsUnitRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "avionics";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<AvionicsUnit> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            AvionicsUnit entity = new AvionicsUnit();
            entity.setCode("FW-%04d".formatted(i + 3 * 1000));
            entity.setName("AvionicsUnit unit %d".formatted(i));
            entity.setStatus(AvionicsUnitStatus.values()[i % AvionicsUnitStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setFirmwareVersion("FW%05d".formatted(i * 7 + 3));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
