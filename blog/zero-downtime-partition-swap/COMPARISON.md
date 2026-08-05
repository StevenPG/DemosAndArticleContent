# COPY + partition swap vs Spring Data JPA `saveAll()`: measured

The partition-swap design in [BLOG.md](./BLOG.md) claims that COPY into
index-free staging tables beats the ordinary ORM write path. This document
measures that claim against a real implementation of the ordinary path, in the
same repository, against the same database, consuming the same Kafka messages.

**Headline:** COPY drained 300,000 messages at **128,205 rows/s**. The same
messages through Spring Data JPA `saveAll()` took **2,629 rows/s** with stock
configuration and **48,709 rows/s** once properly tuned — a **48.8×** and
**2.6×** gap respectively.

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
SELECT. That single change is a large part of the 18.5× gap between the two
profiles.

---

## 3. Write throughput

300,000 preloaded messages, drained from offset 0. PostgreSQL 18.4, 4 vCPU
container. Timing is the app's own first-write-to-last-write window, recorded
in-process — an HTTP poll loop cannot time a drain that finishes in two
seconds.

| Implementation | Drain time | Throughput | Relative |
|---|---|---|---|
| **COPY into staging partitions** | **2,340 ms** | **128,205 rows/s** | **1.0×** |
| JPA `saveAll()` — tuned | 6,159 ms | 48,709 rows/s | 2.6× slower |
| JPA `saveAll()` — naive | 114,109 ms | 2,629 rows/s | **48.8× slower** |

Per-batch latency at comparable batch sizes (~8,200–8,900 rows):

| Implementation | p50 | p99 |
|---|---|---|
| COPY | 71 ms | 193 ms |
| JPA tuned | 704 ms | 905 ms |
| JPA naive | 3,758 ms | 4,429 ms |

Reproduce with `./benchmark.sh 300000`.

### Reading the numbers honestly

- **Tuned JPA is respectable.** 48,709 rows/s is far more than most services
  need. If your ingestion is in the low thousands per second, this benchmark
  is not an argument to rewrite anything.
- **The 2.6× is the real number** for the write mechanism. It comes from
  protocol overhead (per-row parameter binding versus one streamed payload)
  and from maintaining three indexes per row on a live table versus building
  them once per partition off to the side.
- **The 48.8× is a configuration story, not a technology story.** It is what
  the default settings cost, and it is worth knowing precisely because those
  defaults are what most teams are running.
- **This corrects an earlier claim.** The article previously asserted JPA was
  "the wrong tool by two orders of magnitude." Measured, it is 1.7 orders
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
fix the persist-versus-merge behaviour.** That is 18.5× on this hardware, for
two lines of configuration and one interface implementation.

---

## Appendix: method

```bash
docker compose up -d
./benchmark.sh 300000        # writes benchmark-results.txt
```

The harness preloads the topic with a fixed message set while every consumer
is stopped, so no run is timed against a live producer whose rate could drift,
then runs each implementation from offset 0 under its own consumer group with
its target table reset first. Throughput comes from each app's own
`*_drain_seconds` gauge and row counter.

Caveats: a 4-vCPU container with default Postgres server settings — not the
tuned `docker-compose.yml` profile. Absolute numbers are directional; the
relative comparison is what the harness is built to make trustworthy. The
deletes and reads sections were run as SQL against the same database and are
reproducible from the statements shown.
