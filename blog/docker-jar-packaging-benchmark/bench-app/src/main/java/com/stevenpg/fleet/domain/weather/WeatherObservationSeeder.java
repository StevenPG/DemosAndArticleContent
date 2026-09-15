package com.stevenpg.fleet.domain.weather;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WeatherObservationSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final WeatherObservationRepository repository;

    public WeatherObservationSeeder(WeatherObservationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "weather";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<WeatherObservation> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            WeatherObservation entity = new WeatherObservation();
            entity.setCode("MTR-%04d".formatted(i + 22 * 1000));
            entity.setName("WeatherObservation unit %d".formatted(i));
            entity.setStatus(WeatherObservationStatus.values()[i % WeatherObservationStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setMetar("MTR%05d".formatted(i * 7 + 22));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
