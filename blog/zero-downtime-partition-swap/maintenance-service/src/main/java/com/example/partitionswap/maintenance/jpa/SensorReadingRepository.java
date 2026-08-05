package com.example.partitionswap.maintenance.jpa;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Standard Spring Data JPA over the partitioned parent. The derived query
 * below compiles to a WHERE on (device_id, metric) with ORDER BY ingested_at —
 * served by the partitioned {@code sensor_readings_device_metric_idx}, pruned
 * to the relevant partitions by the planner. The native query shows explicit
 * SQL with a time-range predicate on the partition key, which is what enables
 * partition pruning: EXPLAIN it and watch old partitions disappear from the
 * plan.
 */
public interface SensorReadingRepository extends JpaRepository<SensorReading, SensorReadingId> {

    List<SensorReading> findByDeviceIdAndMetricOrderByIngestedAtDesc(String deviceId, String metric, Limit limit);

    List<SensorReading> findByIngestedAtGreaterThanEqualOrderByIngestedAtDesc(Instant since, Limit limit);

    @Query(value = """
            SELECT metric,
                   count(*)     AS sampleCount,
                   min(reading) AS minReading,
                   avg(reading) AS avgReading,
                   max(reading) AS maxReading
            FROM sensor_readings
            WHERE ingested_at >= :since
            GROUP BY metric
            ORDER BY metric
            """, nativeQuery = true)
    List<MetricStats> aggregateSince(@Param("since") Instant since);

    interface MetricStats {
        String getMetric();

        long getSampleCount();

        double getMinReading();

        double getAvgReading();

        double getMaxReading();
    }
}
