package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.common.SensorReadingEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real COPY path against a real Postgres: rows land in the
 * arrival-minute staging table, and that table is index-free (the whole point
 * of staging). Skipped automatically where Docker isn't available.
 */
@Testcontainers(disabledWithoutDocker = true)
class CopyBatchWriterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).load().migrate();
    }

    @Test
    void copiesBatchIntoCurrentMinuteStagingTable() {
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        CopyBatchWriter writer = new CopyBatchWriter(dataSource, new StagingTableManager(jdbcClient));

        List<SensorReadingEvent> batch = IntStream.range(0, 60)
                .mapToObj(i -> new SensorReadingEvent(
                        UUID.randomUUID(), "device-%03d".formatted(i % 8), "temperature_c", i, Instant.now()))
                .toList();

        long written = writer.write(batch);
        assertThat(written).isEqualTo(60);

        String stagingTable = Partitions.windowFor(Instant.now()).tableName();
        Integer rows = jdbcClient.sql("SELECT count(*) FROM " + stagingTable).query(Integer.class).single();
        assertThat(rows).isEqualTo(60);

        // The staging table must be an index-free heap while it's being loaded.
        Integer indexes = jdbcClient.sql("SELECT count(*) FROM pg_indexes WHERE tablename = ?")
                .param(stagingTable)
                .query(Integer.class)
                .single();
        assertThat(indexes).isZero();

        // And it must not yet be visible through the parent.
        Integer visibleThroughParent = jdbcClient.sql("SELECT count(*) FROM " + Partitions.PARENT_TABLE)
                .query(Integer.class)
                .single();
        assertThat(visibleThroughParent).isZero();
    }
}
