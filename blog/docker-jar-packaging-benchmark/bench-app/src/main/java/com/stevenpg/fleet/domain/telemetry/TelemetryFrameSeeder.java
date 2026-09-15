package com.stevenpg.fleet.domain.telemetry;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TelemetryFrameSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final TelemetryFrameRepository repository;

    public TelemetryFrameSeeder(TelemetryFrameRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "telemetry";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<TelemetryFrame> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            TelemetryFrame entity = new TelemetryFrame();
            entity.setCode("FRM-%04d".formatted(i + 5 * 1000));
            entity.setName("TelemetryFrame unit %d".formatted(i));
            entity.setStatus(TelemetryFrameStatus.values()[i % TelemetryFrameStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setFrameId("FRM%05d".formatted(i * 7 + 5));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
