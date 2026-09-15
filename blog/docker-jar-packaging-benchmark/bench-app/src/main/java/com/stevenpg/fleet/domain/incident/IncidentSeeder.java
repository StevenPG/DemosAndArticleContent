package com.stevenpg.fleet.domain.incident;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IncidentSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final IncidentRepository repository;

    public IncidentSeeder(IncidentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "incident";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Incident> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Incident entity = new Incident();
            entity.setCode("IR-%04d".formatted(i + 15 * 1000));
            entity.setName("Incident unit %d".formatted(i));
            entity.setStatus(IncidentStatus.values()[i % IncidentStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setReportNumber("IR%05d".formatted(i * 7 + 15));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
