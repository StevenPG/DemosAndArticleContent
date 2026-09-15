package com.stevenpg.fleet.domain.airport;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AirportSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final AirportRepository repository;

    public AirportSeeder(AirportRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "airport";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Airport> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Airport entity = new Airport();
            entity.setCode("ICAO-%04d".formatted(i + 12 * 1000));
            entity.setName("Airport unit %d".formatted(i));
            entity.setStatus(AirportStatus.values()[i % AirportStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setIcaoCode("ICAO%05d".formatted(i * 7 + 12));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
