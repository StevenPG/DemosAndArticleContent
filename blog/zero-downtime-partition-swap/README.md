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

```
                    ┌────────────────── ingest-service (8080) ──────────────────┐
 producer (1/s) ──► │ Kafka topic ──► batch consumer ──► COPY FROM STDIN        │
                    └──────────────────────────────┬────────────────────────────┘
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
- **`ingest-service/`** — demo producer (~1 msg/s), Kafka **batch** consumer,
  pgjdbc `CopyManager` writer, Flyway migration that owns the schema.
- **`maintenance-service/`** — swap scheduler, retention
  (`DETACH PARTITION CONCURRENTLY` + `DROP`), partition observability endpoints,
  and the Spring Data JPA read API over the parent table.

## Run it

```bash
docker compose up -d                       # Postgres 18 + Kafka (KRaft) + Kafka UI
./gradlew :ingest-service:bootRun          # terminal 1
./gradlew :maintenance-service:bootRun     # terminal 2
```

Within a minute or two you'll see the lifecycle in the logs:

```
ingest-service       : staging table ready: sensor_readings_p20260805_0228 [...]
ingest-service       : COPY 4 rows -> sensor_readings_p20260805_0228 in 2361 µs (1694 rows/s)
maintenance-service  : [sensor_readings_p20260805_0228] promoted to live partition: ~60 rows |
                       pk 3 ms, indexes 4 ms, bounds-check 1 ms, analyze 1 ms, attach 2 ms, total 16 ms
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
```

Or from `psql` (`psql -h localhost -U demo partition_swap`, password `demo`):

```sql
-- watch tables graduate from detached staging to attached partition
SELECT c.relname,
       EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid) AS attached
FROM pg_class c
WHERE c.relname LIKE 'sensor_readings_p%' ORDER BY 1;

-- prove partition pruning on the read path
EXPLAIN SELECT * FROM sensor_readings
WHERE ingested_at >= now() - interval '2 minutes';
```

## Crank it up

The demo defaults to ~60 messages/minute so each partition is readable. The
write path is one `COPY` per consumed Kafka batch, so it takes thousands of
rows per second without code changes:

```yaml
# ingest-service application.yaml
demo:
  producer:
    messages-per-second: 2000
```

## Tests

```bash
./gradlew test
```

Unit tests cover the naming contract and COPY text encoding. The integration
tests (Testcontainers, skipped automatically without Docker) drive the real
COPY path and the full promote → attach → retention lifecycle against
Postgres 18.
