package com.stevenpg.fleet.domain.flightplan;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FlightPlanSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final FlightPlanRepository repository;

    public FlightPlanSeeder(FlightPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "flightplan";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<FlightPlan> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            FlightPlan entity = new FlightPlan();
            entity.setCode("CS-%04d".formatted(i + 10 * 1000));
            entity.setName("FlightPlan unit %d".formatted(i));
            entity.setStatus(FlightPlanStatus.values()[i % FlightPlanStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setCallsign("CS%05d".formatted(i * 7 + 10));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
