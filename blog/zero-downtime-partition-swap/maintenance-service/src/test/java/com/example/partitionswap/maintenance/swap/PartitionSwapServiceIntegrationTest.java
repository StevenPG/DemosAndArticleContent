package com.example.partitionswap.maintenance.swap;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.maintenance.config.MaintenanceProperties;
import com.example.partitionswap.maintenance.retention.PartitionRetentionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives one full lifecycle against a real Postgres: staging table with rows
 * -> promote (pk + indexes + bounds check + analyze + attach) -> rows visible
 * through the parent with all partition indexes in place -> retention detaches
 * and drops it. Skipped automatically where Docker isn't available.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PartitionSwapServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    // A completed minute, comfortably in the past relative to the fixed clock below.
    static final PartitionWindow WINDOW = Partitions.windowFor(Instant.parse("2026-08-05T14:32:00Z"));
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T14:35:00Z"), ZoneOffset.UTC);
    static final MaintenanceProperties PROPS =
            new MaintenanceProperties(5, 2000, 3, new MaintenanceProperties.Retention(true, 1));

    static DataSource dataSource;
    static JdbcClient jdbcClient;
    static PartitionCatalog catalog;
    static PartitionSwapService swapService;

    @BeforeAll
    static void setUpSchemaAndStagingTable() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        jdbcClient = JdbcClient.create(ds);
        catalog = new PartitionCatalog(jdbcClient);
        swapService = new PartitionSwapService(dataSource, catalog, PROPS, CLOCK);

        // Same schema the ingest service's Flyway migration creates.
        jdbcClient.sql("""
                CREATE TABLE sensor_readings (
                    id          uuid             NOT NULL,
                    device_id   text             NOT NULL,
                    metric      text             NOT NULL,
                    reading     double precision NOT NULL,
                    recorded_at timestamptz      NOT NULL,
                    ingested_at timestamptz      NOT NULL,
                    CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, ingested_at)
                ) PARTITION BY RANGE (ingested_at)
                """).update();
        jdbcClient.sql("CREATE INDEX sensor_readings_ingested_at_idx ON sensor_readings (ingested_at)").update();
        jdbcClient.sql("CREATE INDEX sensor_readings_device_metric_idx ON sensor_readings (device_id, metric, ingested_at)")
                .update();

        // What the ingest service leaves behind: an index-free clone with 60 rows.
        jdbcClient.sql("CREATE TABLE %s (LIKE sensor_readings INCLUDING DEFAULTS INCLUDING STORAGE)"
                .formatted(WINDOW.tableName())).update();
        for (int i = 0; i < 60; i++) {
            jdbcClient.sql("INSERT INTO %s (id, device_id, metric, reading, recorded_at, ingested_at) VALUES (?, ?, ?, ?, ?, ?)"
                            .formatted(WINDOW.tableName()))
                    .params(UUID.randomUUID(), "device-%03d".formatted(i % 8), "temperature_c", (double) i,
                            WINDOW.start().atOffset(ZoneOffset.UTC), WINDOW.start().plusSeconds(i).atOffset(ZoneOffset.UTC))
                    .update();
        }
    }

    @Test
    @Order(1)
    void promotesCompletedStagingTableToLivePartition() {
        assertThat(catalog.detachedStagingTables()).containsExactly(WINDOW);

        int promoted = swapService.promoteEligibleStagingTables();
        assertThat(promoted).isEqualTo(1);

        // Attached and gone from the staging list.
        assertThat(catalog.detachedStagingTables()).isEmpty();
        assertThat(catalog.attachedPartitions())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.tableName()).isEqualTo(WINDOW.tableName());
                    assertThat(p.bounds()).contains("FOR VALUES FROM");
                });

        // All 60 rows visible through the parent.
        Integer visible = jdbcClient.sql("SELECT count(*) FROM sensor_readings").query(Integer.class).single();
        assertThat(visible).isEqualTo(60);

        // The partition carries the pk + both partitioned indexes...
        Integer indexes = jdbcClient.sql("SELECT count(*) FROM pg_indexes WHERE tablename = ?")
                .param(WINDOW.tableName()).query(Integer.class).single();
        assertThat(indexes).isEqualTo(3);

        // ...and the scaffolding bounds CHECK was dropped after the attach.
        Integer boundsChecks = jdbcClient.sql(
                        "SELECT count(*) FROM pg_constraint WHERE conrelid = ?::regclass AND conname = ?")
                .params(WINDOW.tableName(), WINDOW.tableName() + "_bounds")
                .query(Integer.class).single();
        assertThat(boundsChecks).isZero();
    }

    @Test
    @Order(2)
    void secondTickIsANoOp() {
        assertThat(swapService.promoteEligibleStagingTables()).isZero();
    }

    @Test
    @Order(3)
    void retentionDetachesConcurrentlyAndDrops() {
        PartitionRetentionService retention =
                new PartitionRetentionService(jdbcClient, catalog, PROPS, CLOCK);

        retention.dropExpiredPartitions();

        assertThat(catalog.attachedPartitions()).isEmpty();
        Boolean tableGone = jdbcClient.sql("SELECT to_regclass(?) IS NULL")
                .param(WINDOW.tableName()).query(Boolean.class).single();
        assertThat(tableGone).isTrue();
    }
}
