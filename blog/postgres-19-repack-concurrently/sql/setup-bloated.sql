-- A bloated table: insert :rows rows, delete 70% of them, plain VACUUM.
-- Plain VACUUM marks the space reusable but does not give it back to the OS,
-- so the table and index stay at full size - exactly what REPACK is for.
\set ON_ERROR_STOP on
DROP TABLE IF EXISTS positions;
CREATE TABLE positions (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aircraft_id int              NOT NULL,
    ts          timestamptz      NOT NULL,
    lat         double precision NOT NULL,
    lon         double precision NOT NULL,
    altitude_ft int              NOT NULL,
    note        text
);
INSERT INTO positions (aircraft_id, ts, lat, lon, altitude_ft, note)
SELECT (random() * 19999)::int + 1,
       now() - (g || ' seconds')::interval,
       25 + random() * 24,
       -125 + random() * 58,
       (random() * 45000)::int,
       repeat('x', 40 + (g % 60))
FROM generate_series(1, :rows) AS g;
CREATE INDEX positions_aircraft_ts ON positions (aircraft_id, ts DESC);
DELETE FROM positions WHERE id % 10 < 7;
VACUUM (ANALYZE) positions;
