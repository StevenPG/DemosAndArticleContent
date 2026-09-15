package com.stevenpg.fleet.domain.supplier;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SupplierSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final SupplierRepository repository;

    public SupplierSeeder(SupplierRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "supplier";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Supplier> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Supplier entity = new Supplier();
            entity.setCode("CAGE-%04d".formatted(i + 18 * 1000));
            entity.setName("Supplier unit %d".formatted(i));
            entity.setStatus(SupplierStatus.values()[i % SupplierStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setCageCode("CAGE%05d".formatted(i * 7 + 18));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
