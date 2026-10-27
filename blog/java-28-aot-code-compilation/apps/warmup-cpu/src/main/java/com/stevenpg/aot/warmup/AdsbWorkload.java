package com.stevenpg.aot.warmup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.regex.Pattern;

/**
 * Decode ~200k ADS-B-style CSV position reports, rebuild per-aircraft tracks, compute
 * great-circle distance and climb rates, flag odd callsigns, and format a report.
 * Parsing, hashing, sorting, floating point and string formatting: a typical slice of
 * a telemetry pipeline, and a broad spread of JDK code for the JIT to compile.
 */
final class AdsbWorkload implements WarmupMain.Workload {

    private static final int AIRCRAFT = 2_000;
    private static final int REPORTS = 200_000;
    private static final Pattern MILITARY_OR_TEST = Pattern.compile("^(RCH|NAVY|TEST|N\\d{3}[A-Z]{2})\\d*$");

    record Report(String icao24, String callsign, long t, double lat, double lon, double altFt, double speedKt) {
    }

    record TrackStats(String icao24, String callsign, int points, double km, double maxAlt, double maxClimbFpm,
                      boolean flagged) {
    }

    private final List<String> lines;

    AdsbWorkload() {
        RandomGenerator rnd = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
        String[] prefixes = {"AAL", "DAL", "UAL", "SWA", "RCH", "NAVY", "TEST", "N123AB", "JBU", "FFT"};
        lines = new ArrayList<>(REPORTS);
        double[] lat = new double[AIRCRAFT], lon = new double[AIRCRAFT], alt = new double[AIRCRAFT];
        for (int a = 0; a < AIRCRAFT; a++) {
            lat[a] = 25 + rnd.nextDouble() * 24;
            lon[a] = -124 + rnd.nextDouble() * 57;
            alt[a] = rnd.nextDouble() * 38_000;
        }
        for (int i = 0; i < REPORTS; i++) {
            int a = rnd.nextInt(AIRCRAFT);
            lat[a] += (rnd.nextDouble() - 0.5) * 0.05;
            lon[a] += (rnd.nextDouble() - 0.5) * 0.05;
            alt[a] = Math.clamp(alt[a] + (rnd.nextDouble() - 0.45) * 800, 0, 43_000);
            String callsign = prefixes[a % prefixes.length] + (a % 1000);
            lines.add("%06x,%s,%d,%.5f,%.5f,%.0f,%.1f".formatted(0xA00000 + a, callsign,
                    1_700_000_000L + i, lat[a], lon[a], alt[a], 250 + rnd.nextDouble() * 250));
        }
    }

    @Override
    public long runOnce() {
        Map<String, List<Report>> tracks = new HashMap<>();
        for (String line : lines) {
            Report r = parse(line);
            tracks.computeIfAbsent(r.icao24(), k -> new ArrayList<>()).add(r);
        }

        List<TrackStats> stats = new ArrayList<>(tracks.size());
        for (List<Report> track : tracks.values()) {
            track.sort(Comparator.comparingLong(Report::t));
            double km = 0, maxAlt = 0, maxClimb = 0;
            for (int i = 1; i < track.size(); i++) {
                Report p = track.get(i - 1), c = track.get(i);
                km += haversineKm(p.lat(), p.lon(), c.lat(), c.lon());
                maxAlt = Math.max(maxAlt, c.altFt());
                maxClimb = Math.max(maxClimb, (c.altFt() - p.altFt()) / Math.max(1, c.t() - p.t()) * 60);
            }
            Report first = track.getFirst();
            stats.add(new TrackStats(first.icao24(), first.callsign(), track.size(), km, maxAlt, maxClimb,
                    MILITARY_OR_TEST.matcher(first.callsign()).matches()));
        }

        stats.sort(Comparator.comparingDouble(TrackStats::km).reversed());
        StringBuilder report = new StringBuilder();
        for (TrackStats s : stats.subList(0, 50)) {
            report.append("%-8s %-10s %5d pts %9.1f km %7.0f ft %6.0f fpm%s%n".formatted(
                    s.icao24(), s.callsign(), s.points(), s.km(), s.maxAlt(), s.maxClimbFpm(),
                    s.flagged() ? " FLAG" : ""));
        }
        return report.toString().hashCode() + stats.stream().filter(TrackStats::flagged).count();
    }

    private static Report parse(String line) {
        int c1 = line.indexOf(','), c2 = line.indexOf(',', c1 + 1), c3 = line.indexOf(',', c2 + 1),
                c4 = line.indexOf(',', c3 + 1), c5 = line.indexOf(',', c4 + 1), c6 = line.indexOf(',', c5 + 1);
        return new Report(line.substring(0, c1), line.substring(c1 + 1, c2),
                Long.parseLong(line, c2 + 1, c3, 10),
                Double.parseDouble(line.substring(c3 + 1, c4)), Double.parseDouble(line.substring(c4 + 1, c5)),
                Double.parseDouble(line.substring(c5 + 1, c6)), Double.parseDouble(line.substring(c6 + 1)));
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6_371.0088 * Math.asin(Math.sqrt(h));
    }
}
