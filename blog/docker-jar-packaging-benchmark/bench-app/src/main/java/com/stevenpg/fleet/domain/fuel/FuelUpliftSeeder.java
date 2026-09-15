package com.stevenpg.fleet.domain.fuel;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FuelUpliftSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final FuelUpliftRepository repository;

    public FuelUpliftSeeder(FuelUpliftRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "fuel";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<FuelUplift> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            FuelUplift entity = new FuelUplift();
            entity.setCode("TKT-%04d".formatted(i + 21 * 1000));
            entity.setName("FuelUplift unit %d".formatted(i));
            entity.setStatus(FuelUpliftStatus.values()[i % FuelUpliftStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setTicketNumber("TKT%05d".formatted(i * 7 + 21));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
