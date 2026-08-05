-- The baseline table: one ordinary, unpartitioned, fully indexed table that
-- incoming messages are written to directly. This is what the vast majority of
-- Kafka -> Postgres services actually look like.
--
-- Column-for-column identical to the partitioned sensor_readings, and carrying
-- the SAME NUMBER of indexes, because index maintenance is the cost being
-- measured. Anything less would make the comparison meaningless.
CREATE TABLE sensor_readings_jpa (
    id          uuid             NOT NULL,
    device_id   text             NOT NULL,
    metric      text             NOT NULL,
    reading     double precision NOT NULL,
    recorded_at timestamptz      NOT NULL,
    ingested_at timestamptz      NOT NULL,

    -- Deliberately narrower than the partitioned table's (id, ingested_at):
    -- a flat table has no partition key to carry, so this is both the natural
    -- design AND the one that favours the baseline. Smaller index entries,
    -- less to maintain per row. Where the comparison could be tilted either
    -- way, it is tilted towards JPA.
    CONSTRAINT sensor_readings_jpa_pkey PRIMARY KEY (id)
);

-- The same two secondary indexes the partitioned parent defines, serving the
-- same queries.
CREATE INDEX sensor_readings_jpa_ingested_at_idx
    ON sensor_readings_jpa (ingested_at);

CREATE INDEX sensor_readings_jpa_device_metric_idx
    ON sensor_readings_jpa (device_id, metric, ingested_at);
