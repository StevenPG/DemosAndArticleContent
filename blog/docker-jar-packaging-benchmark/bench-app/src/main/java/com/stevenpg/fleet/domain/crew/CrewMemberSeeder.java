package com.stevenpg.fleet.domain.crew;

import com.stevenpg.fleet.seed.SeedContributor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CrewMemberSeeder implements SeedContributor {

    private static final String[] REGIONS = {"NAMER", "EMEA", "APAC", "LATAM"};

    private final CrewMemberRepository repository;

    public CrewMemberSeeder(CrewMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public String domain() {
        return "crew";
    }

    @Override
    @Transactional
    public int seed(int rows) {
        List<CrewMember> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            CrewMember entity = new CrewMember();
            entity.setCode("LIC-%04d".formatted(i + 9 * 1000));
            entity.setName("CrewMember unit %d".formatted(i));
            entity.setStatus(CrewMemberStatus.values()[i % CrewMemberStatus.values().length]);
            entity.setRegion(REGIONS[i % REGIONS.length]);
            entity.setLicenseNumber("LIC%05d".formatted(i * 7 + 9));
            entity.setMassKg((i + 1) * 13.5d);
            entity.setCycles(i * 17);
            entity.setActive(i % 5 != 0);
            batch.add(entity);
        }
        return repository.saveAll(batch).size();
    }
}
