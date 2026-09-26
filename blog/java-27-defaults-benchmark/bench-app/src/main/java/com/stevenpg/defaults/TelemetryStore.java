package com.stevenpg.defaults;

import java.util.Collection;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * An in-memory telemetry cache: aircraft id -> last N positions. Seeded with a
 * fixed random seed before the app reports ready, so every run on every JDK
 * holds byte-for-byte the same data.
 */
@Component
public class TelemetryStore implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelemetryStore.class);

    private final Map<Long, Track> tracks = new ConcurrentHashMap<>();
    private final int aircraft;
    private final int positionsPerAircraft;
    private volatile long seedMillis = -1;

    public TelemetryStore(@Value("${bench.aircraft}") int aircraft,
                          @Value("${bench.positions-per-aircraft}") int positionsPerAircraft) {
        this.aircraft = aircraft;
        this.positionsPerAircraft = positionsPerAircraft;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        var random = new SplittableRandom(27);
        long now = 1_790_000_000_000L;
        for (long id = 1; id <= aircraft; id++) {
            var track = new Track(id, "N%05dSP".formatted(id), positionsPerAircraft);
            double lat = 25 + random.nextDouble(24);
            double lon = -125 + random.nextDouble(58);
            for (int p = 0; p < positionsPerAircraft; p++) {
                lat += random.nextDouble(-0.01, 0.01);
                lon += random.nextDouble(-0.01, 0.01);
                track.append(new Position(now + p * 1_000L, lat, lon,
                        random.nextInt(0, 45_000), (short) random.nextInt(80, 520), (short) random.nextInt(0, 360)));
            }
            tracks.put(id, track);
        }
        seedMillis = (System.nanoTime() - start) / 1_000_000;
        log.info("Seeded {} aircraft x {} positions in {} ms", aircraft, positionsPerAircraft, seedMillis);
    }

    public Track track(long id) {
        return tracks.get(id);
    }

    public Collection<Track> tracks() {
        return tracks.values();
    }

    public int aircraftCount() {
        return aircraft;
    }

    public long positionCount() {
        long total = 0;
        for (Track track : tracks.values()) {
            total += track.size();
        }
        return total;
    }

    public long seedMillis() {
        return seedMillis;
    }
}
