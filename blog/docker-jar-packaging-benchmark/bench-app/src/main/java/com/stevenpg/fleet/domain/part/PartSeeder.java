package com.stevenpg.fleet.domain.part;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PartSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final PartRepository repository;

    public PartSeeder(PartRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "part";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Part> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Part entity = new Part();
            entity.setCode("PN-%04d".formatted(i + 17 * 1000));
            entity.setName("Part unit %d".formatted(i));
            entity.setStatus(PartStatus.values()[i % PartStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setPartNumber("PN%05d".formatted(i * 7 + 17));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
