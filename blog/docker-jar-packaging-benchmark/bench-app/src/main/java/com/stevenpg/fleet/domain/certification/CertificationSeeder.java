package com.stevenpg.fleet.domain.certification;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CertificationSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final CertificationRepository repository;

    public CertificationSeeder(CertificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "certification";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<Certification> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            Certification entity = new Certification();
            entity.setCode("AUTH-%04d".formatted(i + 19 * 1000));
            entity.setName("Certification unit %d".formatted(i));
            entity.setStatus(CertificationStatus.values()[i % CertificationStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setAuthority("AUTH%05d".formatted(i * 7 + 19));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
