package com.example.partitionswap.ingest.copy;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import com.example.partitionswap.common.SensorReadingEvent;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
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
 *
 * <p>Rows are streamed to the server in fixed-size chunks through pgjdbc's
 * low-level {@link CopyIn} handle rather than handed over as one finished
 * payload, so memory stays flat regardless of batch size. Each listener thread
 * owns a reusable encoder buffer, so a steady-state batch allocates essentially
 * nothing beyond the events themselves.
 */
@Component
public class CopyBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(CopyBatchWriter.class);

    /** One reusable ~72 KB buffer per listener thread, allocated on first use. */
    private static final ThreadLocal<CopyTextEncoder> ENCODER =
            ThreadLocal.withInitial(CopyTextEncoder::new);

    private final DataSource dataSource;
    private final StagingTableManager stagingTableManager;
    private final IngestMetrics metrics;

    public CopyBatchWriter(DataSource dataSource,
                           StagingTableManager stagingTableManager,
                           IngestMetrics metrics) {
        this.dataSource = dataSource;
        this.stagingTableManager = stagingTableManager;
        this.metrics = metrics;
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

        String copySql = "COPY %s (%s) FROM STDIN WITH (FORMAT text)"
                .formatted(window.tableName(), CopyTextEncoder.COLUMNS);

        long startNanos = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
            long rows = streamBatch(copyManager, copySql, events, ingestedAt);
            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordCopy(rows, durationNanos);
            // DEBUG, not INFO: at real ingestion rates a line per batch turns
            // the log appender into a contention point shared by every listener
            // thread. IngestMetrics emits one aggregated line per window instead.
            log.debug("COPY {} rows -> {} in {} µs", rows, window.tableName(), durationNanos / 1_000);
            return rows;
        } catch (SQLException e) {
            metrics.recordFailure();
            // Propagate so the Kafka container does NOT commit offsets; the
            // batch is redelivered and re-COPYed after the error backoff. The
            // exception TYPE decides whether that redelivery repeats forever
            // or the batch is dead-lettered — see classify().
            throw classify(e, window.tableName());
        }
    }

    /**
     * Splits COPY failures into "this batch will never work" and "the database
     * is having a moment", using the SQLState class — the portable, documented
     * part of the error code, rather than message matching.
     *
     * <p>Anything unrecognised is treated as transient on purpose. A blocked
     * partition is visible, alertable, and resumes on its own; a batch
     * dead-lettered because Postgres was mid-failover is silent data loss.
     */
    private RuntimeException classify(SQLException e, String tableName) {
        String sqlState = e.getSQLState();
        String stateClass = sqlState == null || sqlState.length() < 2 ? "" : sqlState.substring(0, 2);
        String message = "COPY into " + tableName + " failed (SQLState " + sqlState + ")";
        return switch (stateClass) {
            // 22 data exception, 23 integrity constraint violation,
            // 42 syntax error or access rule violation (e.g. the staging table
            // no longer matches the encoder's column list after a bad deploy).
            case "22", "23", "42" -> new PoisonBatchException(message, e);
            // 08 connection exception, 40 transaction rollback (deadlock,
            // serialization failure), 53 insufficient resources, 57 operator
            // intervention (shutdown, cancellation), 58 system error.
            default -> new TransientIngestException(message, e);
        };
    }

    private long streamBatch(CopyManager copyManager,
                             String copySql,
                             List<SensorReadingEvent> events,
                             Instant ingestedAt) throws SQLException {
        byte[] ingestedAtBytes = CopyTextEncoder.encodeTimestamp(ingestedAt);
        CopyTextEncoder encoder = ENCODER.get();
        encoder.reset();

        CopyIn copyIn = copyManager.copyIn(copySql);
        try {
            for (SensorReadingEvent event : events) {
                encoder.appendRow(event, ingestedAtBytes);
                if (encoder.length() >= CopyTextEncoder.FLUSH_THRESHOLD) {
                    copyIn.writeToCopy(encoder.buffer(), 0, encoder.length());
                    encoder.reset();
                }
            }
            if (encoder.length() > 0) {
                copyIn.writeToCopy(encoder.buffer(), 0, encoder.length());
            }
            return copyIn.endCopy();
        } catch (SQLException | RuntimeException e) {
            // Without an explicit cancel, an abandoned COPY leaves the
            // connection stuck in COPY mode and the pool hands that broken
            // connection to the next batch.
            if (copyIn.isActive()) {
                try {
                    copyIn.cancelCopy();
                } catch (SQLException cancelFailure) {
                    e.addSuppressed(cancelFailure);
                }
            }
            throw e;
        } finally {
            // Never let one oversized batch pin a grown buffer for the life
            // of the thread.
            encoder.reset();
        }
    }
}
