package com.stevenpg.aot.rest;

import com.stevenpg.aot.rest.TrackController.Position;

import java.util.ArrayList;
import java.util.List;

final class TrackAnalyzer {

    private static final double EARTH_RADIUS_KM = 6_371.0088;

    enum Phase { GROUND, CLIMB, CRUISE, DESCENT }

    record Segment(Phase phase, long startT, long endT, double distanceKm) {
    }

    record TrackSummary(String icao24, int points, double distanceKm, double maxAltFt,
                        double avgGroundSpeedKt, double maxClimbFpm, double maxDescentFpm,
                        List<Segment> segments, String polyline) {
    }

    private TrackAnalyzer() {
    }

    static TrackSummary analyze(String icao24, List<Position> pts) {
        double[] alt = smooth(pts.stream().mapToDouble(Position::altFt).toArray(), 5);
        double distance = 0, maxAlt = 0, speedSum = 0, maxClimb = 0, maxDescent = 0;
        List<Segment> segments = new ArrayList<>();
        Phase current = null;
        long segStart = pts.getFirst().t();
        double segDist = 0;

        for (int i = 1; i < pts.size(); i++) {
            Position a = pts.get(i - 1), b = pts.get(i);
            double d = haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
            distance += d;
            segDist += d;
            maxAlt = Math.max(maxAlt, alt[i]);
            speedSum += b.speedKt();
            double minutes = Math.max(1, b.t() - a.t()) / 60.0;
            double fpm = (alt[i] - alt[i - 1]) / minutes;
            maxClimb = Math.max(maxClimb, fpm);
            maxDescent = Math.min(maxDescent, fpm);

            Phase p = alt[i] < 500 ? Phase.GROUND : fpm > 300 ? Phase.CLIMB : fpm < -300 ? Phase.DESCENT : Phase.CRUISE;
            if (p != current) {
                if (current != null) {
                    segments.add(new Segment(current, segStart, a.t(), segDist));
                }
                current = p;
                segStart = a.t();
                segDist = 0;
            }
        }
        segments.add(new Segment(current, segStart, pts.getLast().t(), segDist));
        return new TrackSummary(icao24, pts.size(), distance, maxAlt, speedSum / (pts.size() - 1),
                maxClimb, maxDescent, segments, encodePolyline(pts));
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    /** Centered moving average - deliberately allocation-heavy, like real code. */
    static double[] smooth(double[] v, int window) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            int lo = Math.max(0, i - window / 2), hi = Math.min(v.length - 1, i + window / 2);
            double sum = 0;
            for (int j = lo; j <= hi; j++) {
                sum += v[j];
            }
            out[i] = sum / (hi - lo + 1);
        }
        return out;
    }

    /** Google encoded-polyline algorithm: bit twiddling + StringBuilder churn. */
    static String encodePolyline(List<Position> pts) {
        StringBuilder sb = new StringBuilder(pts.size() * 8);
        long prevLat = 0, prevLon = 0;
        for (Position p : pts) {
            long lat = Math.round(p.lat() * 1e5), lon = Math.round(p.lon() * 1e5);
            encode(lat - prevLat, sb);
            encode(lon - prevLon, sb);
            prevLat = lat;
            prevLon = lon;
        }
        return sb.toString();
    }

    private static void encode(long v, StringBuilder sb) {
        v = v < 0 ? ~(v << 1) : v << 1;
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
