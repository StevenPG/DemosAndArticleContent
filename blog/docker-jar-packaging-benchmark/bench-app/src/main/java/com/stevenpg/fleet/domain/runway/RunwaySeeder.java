package com.stevenpg.fleet.domain.runway;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RunwaySeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final RunwayRepository repository;

    public RunwaySeeder(RunwayRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "runway";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Runway> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Runway entity = new Runway();
            entity.setCode("RWY-%04d".formatted(i + 13 * 1000));
            entity.setName("Runway unit %d".formatted(i));
            entity.setStatus(RunwayStatus.values()[i % RunwayStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setDesignator("RWY%05d".formatted(i * 7 + 13));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
