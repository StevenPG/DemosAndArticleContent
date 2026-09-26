package com.stevenpg.defaults;

/**
 * One ADS-B style position report. 32 bytes of fields: 44 bytes with a
 * 12-byte header (padded to 48), 40 bytes with an 8-byte header. This is the
 * object the live set is made of, so it is where compact headers pay or don't.
 */
public record Position(long epochMillis, double lat, double lon, int altitudeFt, short groundSpeedKt, short heading) {
}
