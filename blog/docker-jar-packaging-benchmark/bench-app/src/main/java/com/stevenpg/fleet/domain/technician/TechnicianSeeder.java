package com.stevenpg.fleet.domain.technician;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TechnicianSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final TechnicianRepository repository;

    public TechnicianSeeder(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "technician";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Technician> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Technician entity = new Technician();
            entity.setCode("BDG-%04d".formatted(i + 8 * 1000));
            entity.setName("Technician unit %d".formatted(i));
            entity.setStatus(TechnicianStatus.values()[i % TechnicianStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setBadgeId("BDG%05d".formatted(i * 7 + 8));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
