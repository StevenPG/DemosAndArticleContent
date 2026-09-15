package com.stevenpg.fleet.seed;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads a fixed number of rows per domain so every packaging variant serves the
 * same data. Seeding is skipped when {@code bench.seed.rows} is zero, which is
 * what the AOT/CDS training runs do - training only needs the classes loaded,
 * not the rows written.
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final List<SeedContributor> contributors;
    private final int rows;

    public SeedRunner(
            List<SeedContributor> contributors,
            @org.springframework.beans.factory.annotation.Value("${bench.seed.rows:25}") int rows) {
        this.contributors = contributors;
        this.rows = rows;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rows <= 0) {
            log.info("BENCH_SEED_SKIPPED contributors={}", contributors.size());
            return;
        }
        int total = 0;
        for (SeedContributor contributor : contributors) {
            total += contributor.seed(rows);
        }
        log.info("BENCH_SEEDED_ROWS={} domains={}", total, contributors.size());
    }
}
