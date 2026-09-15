package com.stevenpg.fleet.domain.aircraft;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AircraftSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final AircraftRepository repository;

    public AircraftSeeder(AircraftRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "aircraft";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Aircraft> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Aircraft entity = new Aircraft();
            entity.setCode("TAIL-%04d".formatted(i + 1 * 1000));
            entity.setName("Aircraft unit %d".formatted(i));
            entity.setStatus(AircraftStatus.values()[i % AircraftStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setTailNumber("TAIL%05d".formatted(i * 7 + 1));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
