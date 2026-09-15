package com.stevenpg.fleet.domain.engine;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EngineSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final EngineRepository repository;

    public EngineSeeder(EngineRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "engine";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Engine> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Engine entity = new Engine();
            entity.setCode("ESN-%04d".formatted(i + 2 * 1000));
            entity.setName("Engine unit %d".formatted(i));
            entity.setStatus(EngineStatus.values()[i % EngineStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setSerialNumber("ESN%05d".formatted(i * 7 + 2));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
