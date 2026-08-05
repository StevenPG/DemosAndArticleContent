# COPY + partition swap vs Spring Data JPA `saveAll()`: measured

The partition-swap design in [BLOG.md](./BLOG.md) claims that COPY into
index-free staging tables beats the ordinary ORM write path. This document
measures that claim against a real implementation of the ordinary path, in the
same repository, against the same database, consuming the same Kafka messages.

**Headline:** COPY drained 300,000 messages at a median **112,994 rows/s**.
The same messages through Spring Data JPA `saveAll()` managed **2,859 rows/s**
with stock configuration and **46,649 rows/s** once properly tuned — a
**39.5×** and **2.4×** gap respectively, across three runs.

The tuned figure matters more than the naive one. A benchmark that only beats
an unconfigured ORM proves nothing; this one beats a competently configured
one too, by a smaller and more believable margin.

---

## 1. What is being compared

| | `ingest-service` | `jpa-baseline-service` |
|---|---|---|
| Write mechanism | `COPY FROM STDIN`, streamed | `repository.saveAll()` |
| Target | index-free per-minute staging table, attached later | one flat, fully indexed table |
| Table | `sensor_readings` (partitioned) | `sensor_readings_jpa` |
| Indexes | PK + 2 secondary | PK + the same 2 secondary |
| Source | topic `sensor-readings` | the same topic, different consumer group |

Both consume the **same preloaded messages** with the **same batch listener**
and the **same `max.poll.records`**. The Kafka side is identical; the only
variable is what happens to a batch once the application has it.

### Where the comparison is deliberately tilted toward JPA

Anywhere a choice could favour one side, it favours the baseline:

- **Narrower primary key.** The partitioned table must carry `(id,
  ingested_at)` because a partitioned table's unique constraints must include
  the partition key. The flat table uses `(id)` alone — smaller index entries,
  less to maintain per row.
- **No swap work counted.** The COPY path's numbers cover ingestion only; the
  cost of promoting a staging table into a live partition is measured
  separately (§4) and is not credited against JPA.
- **Matched tuning.** The tuned profile gets the same listener concurrency (6),
  the same `synchronous_commit = off`, and the same pool sizing as the COPY
  path.

---

## 2. The two JPA profiles

Both run the **identical Java**. Everything below is configuration.

| | `naive` | `tuned` |
|---|---|---|
| `hibernate.jdbc.batch_size` | 0 (Hibernate's default) | 1000 |
| `hibernate.order_inserts` | false | true |
| Listener concurrency | 1 (Spring's default) | 6 |
| `synchronous_commit` | on (Postgres default) | off |
| Persist vs merge | `merge()` — one SELECT per row | `persist()` |

Nothing in `naive` is a strawman. Every setting is a **default** you get by
adding `spring-boot-starter-data-jpa`, writing `saveAll()`, and shipping.

The persist-versus-merge row is the one most teams never notice. Spring Data
decides between `persist()` and `merge()` by asking whether the entity is new,
and with an **application-assigned id** — which this design requires, because
the id identifies the event across Kafka — the id is never null, so Spring Data
concludes the row already exists and calls `merge()`. Merge makes Hibernate
load current state first: **one SELECT per row, before every INSERT**. Nothing
in the code looks wrong. `saveAll(tenThousandRows)` silently becomes ten
thousand SELECTs and ten thousand INSERTs.

Implementing `Persistable` and returning `true` from `isNew()` removes the
SELECT. That single change is a large part of the ~16× gap between the two
profiles.

---

## 3. Write throughput

300,000 preloaded messages, drained from offset 0. PostgreSQL 18.4, 4 vCPU
container. Timing is the app's own first-write-to-last-write window, recorded
in-process — an HTTP poll loop cannot time a drain that finishes in two
seconds.

| Implementation | Drain time | Throughput (median of 3) | Range | Relative |
|---|---|---|---|---|
| **COPY into staging partitions** | 2,655 ms | **112,994 rows/s** | 95,238 – 128,205 | **1.0×** |
| JPA `saveAll()` — tuned | 7,107 ms | 46,649 rows/s | 42,211 – 48,709 | 2.4× slower |
| JPA `saveAll()` — naive | 104,917 ms | 2,859 rows/s | 2,629 – 2,879 | **39.5× slower** |

Three runs of 300,000 messages each, on an otherwise idle box. Note the
asymmetry in the ranges: the COPY figure varies by about a third run-to-run
while the JPA figures are stable to a few percent. That is expected and worth
understanding — COPY finishes in under three seconds, so page-cache state and
checkpoint timing are a large fraction of its measurement, whereas the JPA
runs are long enough to average that noise out and are bottlenecked on their
own per-row work rather than on the machine. Treat the COPY number as "roughly
100k rows/s on this hardware", not as three significant figures.

Per-batch latency at comparable batch sizes (~8,200–8,900 rows):

| Implementation | p50 | p99 |
|---|---|---|
| COPY | 88 ms | 209 ms |
| JPA tuned | 805 ms | 872 ms |
| JPA naive | 3,423 ms | 3,691 ms |

Reproduce with `./benchmark.sh 300000`.

### Reading the numbers honestly

- **Tuned JPA is respectable.** ~46,000 rows/s is far more than most services
  need. If your ingestion is in the low thousands per second, this benchmark
  is not an argument to rewrite anything.
- **The 2.4× is the real number** for the write mechanism. It comes from
  protocol overhead (per-row parameter binding versus one streamed payload)
  and from maintaining three indexes per row on a live table versus building
  them once per partition off to the side.
- **The 39.5× is a configuration story, not a technology story.** It is what
  the default settings cost, and it is worth knowing precisely because those
  defaults are what most teams are running.
- **This corrects an earlier claim.** The article previously asserted JPA was
  "the wrong tool by two orders of magnitude." Measured, it is 1.6 orders
  naive and 0.4 orders tuned. The claim has been corrected to match the data.

---

## 4. Deletes: retention

Retention is where the gap is structural rather than incremental. Both tables
held the same 300,000 rows; both were asked to drop everything but the last
few seconds.

| | Operation | Time | Space reclaimed | Blocks readers |
|---|---|---|---|---|
| **Partitioned** | `DETACH CONCURRENTLY` + `DROP` | **9.6 ms** | immediately, in full | **never** |
| Flat | `DELETE` (293,391 rows) | 192 ms | **none** — table still 44 MB | no |
| Flat | `VACUUM FULL` to reclaim | 34 ms | full (44 MB → 976 kB) | **ACCESS EXCLUSIVE** |

The wall-clock difference (9.6 ms versus 192 ms) understates it. The `DELETE`
only *marks* rows dead: every one leaves a dead tuple in the heap and in all
three indexes, and the table stays at its full size until vacuumed. Reclaiming
that space needs `VACUUM FULL`, which takes an `ACCESS EXCLUSIVE` lock and
rewrites the table — the exact outage this architecture exists to avoid.

Dropping a partition is O(1) in rows. Deleting from a flat table is O(rows),
plus vacuum debt, plus bloat, forever.

---

## 5. Reads

2,000,000 rows spanning ten one-minute windows, loaded identically into both
layouts with identical indexes.

**Q1 — aggregate over the most recent minute** (200k of 2M rows):

| | Plan | Buffers | Execution |
|---|---|---|---|
| Partitioned | prunes to 1 of 10 partitions, parallel seq scan | **2,478** | 31–33 ms |
| Flat | bitmap index scan over the whole table | 6,040 | 33–54 ms |

The partitioned plan touches **2.4× less data** because pruning eliminates nine
partitions before execution begins. That ratio grows with retained history: at
ten times the retention, the partitioned query still reads one minute while the
flat query's index keeps growing.

**Q2 — indexed lookup by device and metric over a recent window:**

| | Buffers | Execution |
|---|---|---|
| Partitioned | 23 | 0.140 ms |
| Flat | 23 | 0.107 ms |

**No advantage, and marginally slower.** A selective lookup served by a
composite index touches a handful of pages either way, and the partitioned
version pays a little extra planning. Partitioning helps queries that **scan a
time range**; it does nothing for point lookups, and it is worth saying so
plainly rather than claiming a win that is not there.

**On-disk size** for the same 2M rows is effectively identical — 43 MB
partitioned versus 44 MB flat — so none of this is a storage-efficiency
argument.

---

## 6. When the baseline is the right choice

This benchmark is not an argument that everyone should COPY into staging
partitions. The flat-table JPA design is *simpler*, and simpler wins by
default. Choose the baseline when:

- ingestion is comfortably inside a few thousand rows/second;
- retention is handled by something other than bulk deletion, or the table
  never gets large enough for bloat to matter;
- reads are point lookups rather than time-range scans;
- the team's familiarity with JPA is worth more than the throughput headroom.

Choose the partition-swap design when sustained ingestion is high enough that
index maintenance dominates the write path, when old data must be dropped
continuously without lock spikes, or when reads are overwhelmingly
recent-window queries.

And if you keep the JPA path: **at minimum set `hibernate.jdbc.batch_size` and
fix the persist-versus-merge behaviour.** That is roughly 16× on this hardware,
for two lines of configuration and one interface implementation.

---

## Appendix: reproducing this

### Write throughput (§3)

```bash
docker compose up -d
./benchmark.sh 300000        # ~4-6 min; writes benchmark-results.txt
```

Requires `psql`, `curl` and `awk`; the script preflight-checks them. Stop any
running `bootRun` processes first — it manages the apps itself. It preloads the
topic with a fixed message set while every consumer is stopped, then runs each
implementation from offset 0 under its own consumer group, resetting that
implementation's target table first. Throughput comes from each app's own
in-process `*_drain_seconds` gauge, because an HTTP poll loop cannot time a
two-second drain. Each run uses a freshly named topic, so repeat runs do not
accumulate each other's messages.

### Deletes (§4)

After a benchmark run, both tables hold the same rows. Start
`maintenance-service` briefly so the COPY path's staging tables get promoted
into real partitions, then:

```sql
-- Flat table: delete everything but the last instant.
\timing on
DELETE FROM sensor_readings_jpa
WHERE ingested_at < (SELECT max(ingested_at) FROM sensor_readings_jpa);

-- Space is NOT reclaimed; the rows are only marked dead.
SELECT pg_size_pretty(pg_total_relation_size('sensor_readings_jpa')) AS size_after_delete,
       (SELECT count(*) FROM sensor_readings_jpa) AS live_rows;

-- Reclaiming it takes ACCESS EXCLUSIVE and rewrites the table.
VACUUM FULL sensor_readings_jpa;
SELECT pg_size_pretty(pg_total_relation_size('sensor_readings_jpa')) AS size_after_vacuum_full;

-- Partitioned: same volume of data, dropped as a unit.
-- (substitute the partition name from the query below)
SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
WHERE i.inhparent = 'sensor_readings'::regclass;

ALTER TABLE sensor_readings DETACH PARTITION sensor_readings_pYYYYMMDD_HHMM CONCURRENTLY;
DROP TABLE sensor_readings_pYYYYMMDD_HHMM;
```

### Reads (§5)

Loads 2M rows across ten one-minute windows into both layouts, with identical
indexes on each:

```sql
TRUNCATE sensor_readings_jpa;
INSERT INTO sensor_readings_jpa
SELECT uuidv7(), 'device-'||lpad((g%64)::text,3,'0'), 'temperature_c', (g%50)+0.5,
       '2026-08-05 12:00:00+00'::timestamptz + ((g%600)||' s')::interval,
       '2026-08-05 12:00:00+00'::timestamptz + ((g%600)||' s')::interval
FROM generate_series(1,2000000) g;

DO $$
DECLARE m int; t text; s timestamptz;
BEGIN
  FOR m IN 0..9 LOOP
    s := '2026-08-05 12:00:00+00'::timestamptz + (m||' min')::interval;
    t := 'sensor_readings_p' || to_char(s AT TIME ZONE 'UTC','YYYYMMDD_HH24MI');
    EXECUTE format('CREATE TABLE %I (LIKE sensor_readings INCLUDING DEFAULTS INCLUDING STORAGE)', t);
    EXECUTE format($f$INSERT INTO %I SELECT uuidv7(), 'device-'||lpad((g%%64)::text,3,'0'),
        'temperature_c', (g%%50)+0.5, %L::timestamptz + ((g%%60)||' s')::interval,
        %L::timestamptz + ((g%%60)||' s')::interval FROM generate_series(1,200000) g$f$, t, s, s);
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I PRIMARY KEY (id, ingested_at)', t, t||'_pkey');
    EXECUTE format('CREATE INDEX %I ON %I (ingested_at)', t||'_ing', t);
    EXECUTE format('CREATE INDEX %I ON %I (device_id, metric, ingested_at)', t||'_dm', t);
    EXECUTE format('ALTER TABLE sensor_readings ATTACH PARTITION %I FOR VALUES FROM (%L) TO (%L)',
                   t, s, s + interval '1 min');
  END LOOP;
END $$;
ANALYZE sensor_readings; ANALYZE sensor_readings_jpa;

-- Q1: time-range aggregate. Compare "Buffers: shared hit" between the two.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT metric, count(*), avg(reading) FROM sensor_readings
WHERE ingested_at >= '2026-08-05 12:09:00+00' GROUP BY metric;

EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT metric, count(*), avg(reading) FROM sensor_readings_jpa
WHERE ingested_at >= '2026-08-05 12:09:00+00' GROUP BY metric;

-- Q2: indexed point lookup. Expect no meaningful difference.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT * FROM sensor_readings WHERE device_id='device-007' AND metric='temperature_c'
AND ingested_at >= '2026-08-05 12:09:00+00' ORDER BY ingested_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)
SELECT * FROM sensor_readings_jpa WHERE device_id='device-007' AND metric='temperature_c'
AND ingested_at >= '2026-08-05 12:09:00+00' ORDER BY ingested_at DESC LIMIT 20;
```

### Caveats

A 4-vCPU container running PostgreSQL 18.4 with **default server settings** —
not the tuned profile in `docker-compose.yml`, so absolute numbers will differ
on your hardware. The relative comparison is what the harness is built to make
trustworthy: identical input, identical Kafka configuration, identical
indexes, and the tie-breaks going to the baseline.
