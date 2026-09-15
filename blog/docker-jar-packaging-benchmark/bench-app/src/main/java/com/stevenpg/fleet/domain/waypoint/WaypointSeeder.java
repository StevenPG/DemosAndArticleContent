package com.stevenpg.fleet.domain.waypoint;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WaypointSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final WaypointRepository repository;

    public WaypointSeeder(WaypointRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "waypoint";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Waypoint> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Waypoint entity = new Waypoint();
            entity.setCode("FIX-%04d".formatted(i + 11 * 1000));
            entity.setName("Waypoint unit %d".formatted(i));
            entity.setStatus(WaypointStatus.values()[i % WaypointStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setFixName("FIX%05d".formatted(i * 7 + 11));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
