package com.stevenpg.fleet.domain.sensor;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SensorSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final SensorRepository repository;

    public SensorSeeder(SensorRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "sensor";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Sensor> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Sensor entity = new Sensor();
            entity.setCode("CH-%04d".formatted(i + 4 * 1000));
            entity.setName("Sensor unit %d".formatted(i));
            entity.setStatus(SensorStatus.values()[i % SensorStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setChannel("CH%05d".formatted(i * 7 + 4));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
