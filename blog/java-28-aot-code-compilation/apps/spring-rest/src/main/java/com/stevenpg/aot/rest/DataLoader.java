package com.stevenpg.aot.rest;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/** Seeds H2 with a deterministic fleet (fixed seed) so every run does identical work. */
@Configuration
class DataLoader {

    static final String[] OPERATORS = {"AAL", "DAL", "UAL", "SWA", "JBU", "ASA", "FFT", "NKS", "SKW", "RPA"};
    static final String[] TYPES = {"A320", "A321", "B738", "B739", "E175", "CRJ9", "B77W", "A359", "B789", "A21N"};

    @Bean
    ApplicationRunner seedFleet(AircraftRepository repo) {
        return args -> {
            RandomGenerator rnd = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
            List<Aircraft> fleet = new ArrayList<>(5_000);
            for (int i = 0; i < 5_000; i++) {
                String op = OPERATORS[rnd.nextInt(OPERATORS.length)];
                String type = TYPES[rnd.nextInt(TYPES.length)];
                fleet.add(new Aircraft(
                        "%06x".formatted(0xA00000 + i),
                        op + (100 + rnd.nextInt(8_900)),
                        op, type,
                        70 + rnd.nextInt(300),
                        1995 + rnd.nextInt(31),
                        2_000 + rnd.nextDouble() * 13_000));
            }
            repo.saveAll(fleet);
        };
    }
}
