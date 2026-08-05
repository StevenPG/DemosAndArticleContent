package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.common.SensorReadingEvent;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * The hot write path: one Kafka poll batch becomes exactly one COPY into the
 * current minute's staging table.
 *
 * <p>COPY is the fastest ingestion interface Postgres has — one network
 * round-trip and one WAL stream for the whole batch, no per-row planning,
 * no per-statement overhead — and against an index-free, constraint-free
 * staging table it degenerates to a nearly pure sequential heap write.
 * A single COPY is also atomic: it inserts all rows or none, so a mid-batch
 * crash never leaves a partial batch behind (Kafka redelivers the whole batch,
 * giving clean at-least-once semantics).
 */
@Component
public class CopyBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(CopyBatchWriter.class);

    private final DataSource dataSource;
    private final StagingTableManager stagingTableManager;

    public CopyBatchWriter(DataSource dataSource, StagingTableManager stagingTableManager) {
        this.dataSource = dataSource;
        this.stagingTableManager = stagingTableManager;
    }

    /**
     * Stamps the whole batch with a single arrival timestamp, which pins the
     * whole batch to a single staging table: no batch ever straddles a minute
     * boundary, no matter when it commits.
     */
    public long write(List<SensorReadingEvent> events) {
        Instant ingestedAt = Instant.now();
        PartitionWindow window = Partitions.windowFor(ingestedAt);
        stagingTableManager.ensureExists(window);

        String payload = CopyTextEncoder.encode(events, ingestedAt);
        String copySql = "COPY %s (%s) FROM STDIN WITH (FORMAT text)"
                .formatted(window.tableName(), CopyTextEncoder.COLUMNS);

        long startNanos = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
            long rows = copyManager.copyIn(copySql, new StringReader(payload));
            long micros = (System.nanoTime() - startNanos) / 1_000;
            log.info("COPY {} rows -> {} in {} µs ({} rows/s)",
                    rows, window.tableName(), micros, micros == 0 ? "∞" : rows * 1_000_000 / micros);
            return rows;
        } catch (SQLException | IOException e) {
            // Propagate so the Kafka container does NOT commit offsets; the
            // batch is redelivered and re-COPYed after the error backoff.
            throw new IllegalStateException("COPY into " + window.tableName() + " failed", e);
        }
    }
}
