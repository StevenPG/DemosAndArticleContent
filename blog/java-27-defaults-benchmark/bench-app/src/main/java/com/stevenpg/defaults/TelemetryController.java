package com.stevenpg.defaults;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three request types the load generator mixes:
 * <ul>
 *   <li>read a track (small allocation, JSON serialization)</li>
 *   <li>ingest a batch of positions (allocation + eviction: old-gen churn)</li>
 *   <li>scan every retained position (pointer chasing over the whole live set)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class TelemetryController {

    private final TelemetryStore store;

    public TelemetryController(TelemetryStore store) {
        this.store = store;
    }

    @GetMapping("/aircraft/{id}/track")
    public ResponseEntity<List<Position>> track(@PathVariable long id, @RequestParam(defaultValue = "50") int limit) {
        Track track = store.track(id);
        return track == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(track.latest(limit));
    }

    @PostMapping("/aircraft/{id}/positions")
    public ResponseEntity<Map<String, Integer>> ingest(@PathVariable long id, @RequestBody List<Position> batch) {
        Track track = store.track(id);
        if (track == null) {
            return ResponseEntity.notFound().build();
        }
        batch.forEach(track::append);
        return ResponseEntity.ok(Map.of("accepted", batch.size()));
    }

    /** Count and mean ground speed per 5,000 ft altitude band, across every retained position. */
    @GetMapping("/stats/altitude-bands")
    public Map<Integer, Map<String, Number>> altitudeBands() {
        long[] count = new long[10];
        long[] speed = new long[10];
        for (Track track : store.tracks()) {
            track.forEach(p -> {
                int band = Math.min(p.altitudeFt() / 5_000, 9);
                count[band]++;
                speed[band] += p.groundSpeedKt();
            });
        }
        Map<Integer, Map<String, Number>> out = new TreeMap<>();
        for (int band = 0; band < 10; band++) {
            out.put(band * 5_000, Map.of(
                    "positions", count[band],
                    "meanGroundSpeedKt", count[band] == 0 ? 0 : speed[band] / (double) count[band]));
        }
        return out;
    }
}
