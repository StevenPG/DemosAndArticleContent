package com.example.partitionswap.maintenance.api;

import com.example.partitionswap.maintenance.jpa.SensorReading;
import com.example.partitionswap.maintenance.jpa.SensorReadingRepository;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The reader's view: plain Spring Data JPA against the parent table. Note
 * what is absent — nothing here knows partitions exist. A row COPYed into a
 * staging table becomes visible in these endpoints the instant its partition
 * attaches, already indexed and analyzed.
 */
@RestController
@RequestMapping("/api/readings")
public class ReadingsController {

    private final SensorReadingRepository repository;

    public ReadingsController(SensorReadingRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/latest")
    public List<SensorReading> latest(@RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "10") int minutes) {
        Instant since = Instant.now().minus(Duration.ofMinutes(minutes));
        return repository.findByIngestedAtGreaterThanEqualOrderByIngestedAtDesc(since, Limit.of(limit));
    }

    @GetMapping("/device/{deviceId}")
    public List<SensorReading> byDevice(@PathVariable String deviceId,
                                        @RequestParam(defaultValue = "temperature_c") String metric,
                                        @RequestParam(defaultValue = "20") int limit) {
        return repository.findByDeviceIdAndMetricOrderByIngestedAtDesc(deviceId, metric, Limit.of(limit));
    }

    @GetMapping("/stats")
    public List<SensorReadingRepository.MetricStats> stats(@RequestParam(defaultValue = "10") int minutes) {
        return repository.aggregateSince(Instant.now().minus(Duration.ofMinutes(minutes)));
    }
}
