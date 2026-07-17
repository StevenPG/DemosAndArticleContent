-- ============================================================================
-- 02_airspaces.sql — the four demo airspaces over Stone Mountain, GA
--
-- Stone Mountain is an isolated granite dome rising ~250 m out of otherwise
-- flat terrain east of Atlanta (summit ~514 m MSL, surroundings ~290-310 m).
-- Coordinates are real WGS84; the dome sits at roughly 33.805N, -84.145W.
--
--   ALPHA   0-100 m AGL   \ stacked on the same footprint over the dome:
--   BRAVO 100-200 m AGL   / they touch at 100 m AGL but must never overlap
--   CHARLIE 50-150 m AGL  offset to the NE, punches through both A and B
--   DELTA 480-530 m MSL   a fixed-altitude corridor crossing the dome; where
--                         it conflicts depends entirely on the terrain below
-- ============================================================================

TRUNCATE airspace_cells, airspaces;

INSERT INTO airspaces (id, name, altitude_ref, lower_m, upper_m, color, footprint) VALUES
(
    'ALPHA', 'Alpha (surface ops)', 'AGL', 0, 100, '#4d9de0',
    ST_GeomFromText('POLYGON((
        -84.1542 33.7992, -84.1368 33.7992,
        -84.1368 33.8114, -84.1542 33.8114,
        -84.1542 33.7992))', 4326)
),
(
    'BRAVO', 'Bravo (transit layer)', 'AGL', 100, 200, '#3bb273',
    ST_GeomFromText('POLYGON((
        -84.1542 33.7992, -84.1368 33.7992,
        -84.1368 33.8114, -84.1542 33.8114,
        -84.1542 33.7992))', 4326)
),
(
    'CHARLIE', 'Charlie (intruder)', 'AGL', 50, 150, '#e1a132',
    ST_GeomFromText('POLYGON((
        -84.1450 33.8053, -84.1320 33.8053,
        -84.1320 33.8117, -84.1450 33.8117,
        -84.1450 33.8053))', 4326)
),
(
    'DELTA', 'Delta (MSL corridor)', 'MSL', 480, 530, '#9b5de5',
    ST_GeomFromText('POLYGON((
        -84.1650 33.8030, -84.1250 33.8030,
        -84.1250 33.8076, -84.1650 33.8076,
        -84.1650 33.8030))', 4326)
);
