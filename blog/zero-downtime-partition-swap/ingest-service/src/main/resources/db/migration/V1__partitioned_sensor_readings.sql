-- The partitioned parent table. Readers only ever query this table; physical
-- per-minute partitions are attached underneath it by the maintenance service.
--
-- The partition key is ingested_at (arrival time at the database), NOT the
-- event time recorded_at. Arrival time is monotonic from the writer's point of
-- view, so a minute's partition is provably complete once the minute has passed
-- plus a small in-flight grace period. Partitioning by event time would leave
-- every partition forever open to late arrivals and make an automated swap
-- unsafe.
CREATE TABLE sensor_readings (
    id          uuid             NOT NULL,
    device_id   text             NOT NULL,
    metric      text             NOT NULL,
    reading     double precision NOT NULL,
    recorded_at timestamptz      NOT NULL,
    ingested_at timestamptz      NOT NULL,

    -- On a partitioned table every unique constraint must include the
    -- partition key, so the "primary key" is (id, ingested_at). Uniqueness of
    -- id alone is therefore only enforced per partition — an acceptable and
    -- standard trade-off for append-only telemetry.
    CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, ingested_at)
)
PARTITION BY RANGE (ingested_at);

-- Partitioned ("template") indexes. These hold no data themselves; every
-- attached partition must carry a structurally matching index. The maintenance
-- service builds those matching indexes on each staging table BEFORE attaching
-- it, so ATTACH PARTITION only links existing indexes into the template —
-- a metadata-only operation — instead of building indexes under lock.
CREATE INDEX sensor_readings_ingested_at_idx
    ON sensor_readings (ingested_at);

CREATE INDEX sensor_readings_device_metric_idx
    ON sensor_readings (device_id, metric, ingested_at);

-- Deliberately NO DEFAULT partition. A default partition would force every
-- ATTACH PARTITION to scan it (to prove no rows belong to the incoming range),
-- reintroducing exactly the lock-holding work this design removes. Rows are
-- never inserted through the parent, so no partition can ever be "missing".
