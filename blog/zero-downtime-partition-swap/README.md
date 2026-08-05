# Zero-Downtime Partition Swapping with Spring Boot 4, Kafka, and Postgres

Companion project for the article on high-throughput Kafka→Postgres ingestion
using per-minute staging tables, `COPY FROM STDIN`, and zero-downtime
`ATTACH PARTITION` swaps. The full write-up is in [BLOG.md](./BLOG.md).

## What it demonstrates

Kafka events are COPYed into **detached, index-free staging tables** (one per
arrival minute). Once a minute completes, a separate maintenance service builds
the primary key and indexes, validates a bounds `CHECK` constraint, runs
`ANALYZE` — all while the table is invisible to readers — and then attaches it
to the partitioned read table with a metadata-only `ATTACH PARTITION`. Readers
querying the parent table never block and never see an unindexed row.

At 2,000 events/sec (~120,000 rows per partition) on PostgreSQL 18, promotion
costs ~516 ms of work, of which the live parent table is involved for **2 ms**.

```
                    ┌────────────── ingest-service (8080) ─────────────────┐
 producer ────────► │ Kafka topic ─► 6 listener threads ─► COPY FROM STDIN │
                    └──────────────────────────────┬──────────────────────-┘
                                                   ▼
                                    sensor_readings_p20260805_1432      (detached,
                                    sensor_readings_p20260805_1433 ◄──   no indexes)
                    ┌─────────────── maintenance-service (8081) ────────┐
                    │ every minute at :10 —                             │
                    │   ADD PRIMARY KEY, CREATE INDEX ×2 (match parent) │
                    │   ADD CHECK (bounds), ANALYZE                     │
                    │   SET lock_timeout; ATTACH PARTITION (+retry)     │
                    │   DROP redundant CHECK                            │
                    └──────────────────────┬─────────────────────────────┘
                                           ▼
                              sensor_readings (partitioned parent)
                                           ▲
                          Spring Data JPA read API, partition pruning
```

- **`common/`** — the partition-naming contract (`sensor_readings_pYYYYMMDD_HHMM`)
  and the Kafka event record shared by both services.
- **`ingest-service/`** — demo producer, Kafka **batch** consumer across 6
  threads, streaming pgjdbc `CopyIn` writer, Micrometer instrumentation,
  dead-letter handling, Flyway migration that owns the schema.
- **`jpa-baseline-service/`** — the comparison baseline: the same events written
  to a single flat table with Spring Data JPA `saveAll()`, in `naive` and
  `tuned` profiles. See [COMPARISON.md](./COMPARISON.md).
- **`maintenance-service/`** — swap scheduler, retention
  (`DETACH PARTITION CONCURRENTLY` + `DROP`), partition observability endpoints,
  and the Spring Data JPA read API over the parent table.

## Run it

Requires PostgreSQL 18 (the demo uses its built-in `uuidv7()` in tests and
benchmarks) — `docker compose` provides it.

```bash
docker compose up -d                       # Postgres 18 + Kafka (KRaft) + Kafka UI
./gradlew :ingest-service:bootRun          # terminal 1
./gradlew :maintenance-service:bootRun     # terminal 2
```

Within a minute or two you'll see the lifecycle in the logs:

```
ingest-service       : staging table ready: sensor_readings_p20260805_0323 [...]
ingest-service       : ingest: 20000 rows in 100 batches (2000 rows/s, 200 rows/batch)
                       | copy p50 3.92 ms, p99 15.71 ms
maintenance-service  : [sensor_readings_p20260805_0422] promoted to live partition: ~120000 rows |
                       pk 139 ms, indexes 266 ms, bounds-check 13 ms, analyze 85 ms,
                       attach 2 ms, drop-check 1 ms, total 516 ms
```

## Watch the swap happen

```bash
# staging tables waiting for their minute to finish
curl -s localhost:8081/api/partitions/staging | jq

# attached partitions with bounds, row estimates, and sizes
curl -s localhost:8081/api/partitions | jq

# read API (Spring Data JPA over the partitioned parent)
curl -s "localhost:8081/api/readings/latest?limit=5" | jq
curl -s "localhost:8081/api/readings/device/device-001?limit=5" | jq
curl -s "localhost:8081/api/readings/stats?minutes=10" | jq

# COPY throughput and latency percentiles
curl -s localhost:8080/actuator/prometheus | grep '^ingest_'

# swap phase timings — compare phase="attach" against phase="indexes"
curl -s localhost:8081/actuator/prometheus | grep '^partition_swap_phase'

# alarms when promotion falls behind (DOWN + HTTP 503 past the threshold)
curl -s localhost:8081/actuator/health | jq .components.partitionSwap
```

The `partitionSwap` health component is the one to alert on. If the
maintenance service stops promoting, ingestion keeps working and every probe
keeps passing — the only symptom is that readers silently stop seeing recent
data. It reports DOWN once the oldest unattached minute is older than
`maintenance.staleness-threshold-seconds`:

```json
{
  "status": "DOWN",
  "details": {
    "stagingBacklog": 1,
    "oldestUnattachedTable": "sensor_readings_p20260805_0333",
    "oldestUnattachedAgeSeconds": 1881,
    "stalenessThresholdSeconds": 180,
    "reason": "partition promotion is falling behind; readers cannot see data older than the threshold"
  }
}
```

Or from `psql` (`psql -h localhost -U demo partition_swap`, password `demo`):

```sql
-- watch tables graduate from detached staging to attached partition
SELECT c.relname,
       EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid) AS attached
FROM pg_class c
WHERE c.relname LIKE 'sensor_readings_p%' ORDER BY 1;

-- prove partition pruning on the read path ("Subplans Removed: N")
EXPLAIN SELECT count(*) FROM sensor_readings
WHERE ingested_at >= now() - interval '1 minute';

-- confirm each partition's indexes are LINKED to the parent's partitioned
-- indexes (which is why ATTACH is metadata-only, not a build)
SELECT parent.relname AS parent_index, child.relname AS partition_index
FROM pg_inherits i
JOIN pg_class parent ON parent.oid = i.inhparent
JOIN pg_class child  ON child.oid  = i.inhrelid
WHERE parent.relkind = 'I';
```

## Crank it up

The write path is one `COPY` per consumed Kafka batch across six listener
threads, so it takes thousands of rows per second without code changes:

```bash
./gradlew :ingest-service:bootRun --args='--demo.producer.messages-per-second=5000'
```

Knobs worth knowing (all in `ingest-service/src/main/resources/application.yaml`):

| Setting | Why it matters |
|---|---|
| `spring.kafka.listener.concurrency` | One COPY stream per topic partition. Default of 1 caps the whole write path at one thread. |
| `spring.datasource.hikari.maximum-pool-size` | Must be ≥ listener concurrency or writers serialize on connection checkout. |
| `max.poll.records` / `fetch.max.wait.ms` | Shape how many rows each COPY carries. Denser batches are better for COPY. |
| `max.poll.interval.ms` | Must exceed the worst-case COPY, or heavy batches trigger rebalance loops. |
| `connection-init-sql: SET synchronous_commit = off` | ~15% better COPY p50 on PG18 (~6× at p99 on PG16 — re-measure after upgrades). Safe here because Kafka replays anything lost. |

The `docker-compose.yml` Postgres service also carries a bulk-ingest profile
(`max_wal_size`, `checkpoint_timeout`, `wal_compression`, `maintenance_work_mem`)
with comments explaining each choice.

## Failure handling

Malformed records go to `sensor-readings.DLT` as raw bytes and the consumer
carries on; database outages retry forever with capped backoff rather than
discarding data. To see it:

```bash
echo 'THIS IS NOT JSON {{{' | kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic sensor-readings

kafka-console-consumer.sh --bootstrap-server localhost:9092 \
    --topic sensor-readings.DLT --from-beginning
```

## Compare against plain Spring Data JPA

```bash
./benchmark.sh 300000        # preloads the topic, drains it three ways
```

| Implementation | Throughput | Relative |
|---|---|---|
| COPY into staging partitions | **128,205 rows/s** | 1.0× |
| `saveAll()`, tuned | 48,709 rows/s | 2.6× slower |
| `saveAll()`, stock defaults | 2,629 rows/s | 48.8× slower |

Retention: 9.6 ms to detach and drop a partition, versus a 192 ms `DELETE`
that reclaims no space and needs a blocking `VACUUM FULL`. Reads: 2.4× fewer
buffers on time-range scans, and no advantage at all on indexed point lookups.
Full method, caveats, and the "when the baseline is the right choice" section
are in [COMPARISON.md](./COMPARISON.md).

## Tests

```bash
./gradlew test
```

Unit tests cover the naming contract, the COPY encoder (UUID equivalence
against `UUID.toString`, escaping, multi-byte and surrogate-pair text, buffer
growth and reuse), and the failure-handling policy — SQLState classification
in both directions, the poison-record path, and the retry-versus-dead-letter
configuration. The integration tests (Testcontainers, skipped automatically
without Docker) drive the real COPY path and the full promote → attach →
retention lifecycle against Postgres 18, seeded with PG18's built-in
`uuidv7()` so the key distribution matches what the producer emits.
