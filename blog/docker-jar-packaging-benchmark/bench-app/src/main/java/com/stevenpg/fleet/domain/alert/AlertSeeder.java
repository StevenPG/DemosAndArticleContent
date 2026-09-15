package com.stevenpg.fleet.domain.alert;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AlertSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final AlertRepository repository;

    public AlertSeeder(AlertRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "alert";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Alert> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Alert entity = new Alert();
            entity.setCode("SEV-%04d".formatted(i + 14 * 1000));
            entity.setName("Alert unit %d".formatted(i));
            entity.setStatus(AlertStatus.values()[i % AlertStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setSeverityLabel("SEV%05d".formatted(i * 7 + 14));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
