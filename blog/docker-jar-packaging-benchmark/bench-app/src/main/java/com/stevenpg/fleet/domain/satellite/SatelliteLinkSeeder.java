package com.stevenpg.fleet.domain.satellite;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SatelliteLinkSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final SatelliteLinkRepository repository;

    public SatelliteLinkSeeder(SatelliteLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "satellite";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<SatelliteLink> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            SatelliteLink entity = new SatelliteLink();
            entity.setCode("NRD-%04d".formatted(i + 23 * 1000));
            entity.setName("SatelliteLink unit %d".formatted(i));
            entity.setStatus(SatelliteLinkStatus.values()[i % SatelliteLinkStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setNoradId("NRD%05d".formatted(i * 7 + 23));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
