---
title: "Zero-downtime partition swapping: high-throughput Kafka ingestion into Postgres with Spring Boot 4"
date: 2026-08-05
tags: [spring-boot, kafka, postgres, partitioning, performance, java]
---

# Zero-downtime partition swapping: high-throughput Kafka ingestion into Postgres with Spring Boot 4

There's a tax on every row you insert into an indexed table. Each `INSERT`
into a table with three indexes is really four writes — one heap tuple and
three index tuples — plus the WAL for all of them, plus the buffer churn of
keeping three B-trees hot while they're being mutated at the same moment
readers are traversing them. At a few hundred rows per second nobody notices.
At thousands per second, index maintenance *is* your write path, and the same
indexes your dashboards depend on are what's throttling ingestion.

The classic warehouse answer is: **don't index while you load**. Load into a
bare table as fast as the disk will take it, build the indexes once at the
end (a bulk index build over N rows is dramatically cheaper than N incremental
index insertions), then swap the loaded table into the readers' view in one
atomic metadata operation. Readers never see a half-loaded table, never wait
on a lock, and never touch an unindexed row.

Postgres has first-class machinery for exactly this — declarative partitioning
plus `ATTACH PARTITION` — and this post builds the whole loop with Spring
Boot 4: a Kafka consumer that `COPY`s events into per-minute staging tables,
and a separate maintenance service that indexes each completed minute and
attaches it, zero-downtime, once a minute. The demo trickles ~60 events/minute
so you can watch each stage happen, but every design decision is made as if
the topic carried thousands of events per second — the point is the shape,
not the volume.

The complete runnable project is in this folder ([README](./README.md) for
the commands). Here's why it's built the way it is.

## The shape of the system

```
                     ┌────────────────── ingest-service ─────────────────────┐
  producer (1/s) ──► │  Kafka topic ──► batch consumer ──► COPY FROM STDIN   │
                     └────────────────────────────────┬─────────────────────-┘
                                                      ▼
                                     sensor_readings_p20260805_1432    ◄─ detached,
                                     sensor_readings_p20260805_1433       index-free
                     ┌─────────────── maintenance-service ──────────────┐
                     │  every minute at :10 —                           │
                     │    1. ADD PRIMARY KEY, CREATE INDEX ×2           │
                     │    2. ADD CHECK (bounds)   ── validation scan    │
                     │    3. ANALYZE                                    │
                     │    4. SET lock_timeout; ATTACH PARTITION (+retry)│
                     │    5. DROP the now-redundant CHECK               │
                     └──────────────────────┬─────────────────────────--┘
                                            ▼
                          sensor_readings   (partitioned parent)
                                            ▲
                        Spring Data JPA read API — never blocked,
                        never sees an unindexed or half-loaded row
```

Two Spring Boot 4 services, deliberately separate processes:

- **ingest-service** owns the hot write path. It must never do anything slower
  than a `COPY`. Index builds are CPU- and I/O-hungry; if they ran in this
  process they could stall `poll()` loops and trigger consumer-group
  rebalances. Isolation means a slow index build can never apply backpressure
  to Kafka consumption.
- **maintenance-service** owns DDL orchestration: the swap, retention, and —
  since it already knows the partition layout — the read API and partition
  observability endpoints.

They share no state and never talk to each other. The Postgres catalog is the
coordination channel, which we'll come back to.

## Why COPY, not INSERT

Spring Data JPA is in this project — on the read side. On the write side it
would be the wrong tool by two orders of magnitude, and it's worth being
precise about why.

A JPA `saveAll()` of 10,000 entities is, at best (with
`hibernate.jdbc.batch_size` tuned, ids pre-assigned, versionless entities),
10,000 rows funneled through the extended query protocol in batches, each
batch a round-trip, each row planned into an `INSERT`, all of it flowing
through the persistence context's dirty-checking machinery. `JdbcTemplate`
batching drops the ORM overhead but keeps the protocol overhead.

`COPY ... FROM STDIN` is a different protocol mode, not a faster INSERT. The
pgjdbc driver streams raw row data over the wire; Postgres parses it straight
into heap tuples. One statement, one stream, one commit for the entire batch.
The consumer's whole write path is:

```java
Instant ingestedAt = Instant.now();
PartitionWindow window = Partitions.windowFor(ingestedAt);
stagingTableManager.ensureExists(window);

String payload = CopyTextEncoder.encode(events, ingestedAt);
String copySql = "COPY %s (%s) FROM STDIN WITH (FORMAT text)"
        .formatted(window.tableName(), CopyTextEncoder.COLUMNS);

try (Connection connection = dataSource.getConnection()) {
    CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
    long rows = copyManager.copyIn(copySql, new StringReader(payload));
}
```

Batches come from Kafka for free. `spring.kafka.listener.type: batch` hands
the listener everything one `poll()` returned — up to `max.poll.records` —
as a single `List`, and commits offsets only after the listener returns.
One poll, one COPY, one offset commit. A single COPY is atomic (all rows or
none), so a crash mid-batch means Kafka redelivers the whole batch and the
staging table never holds a partial one: clean at-least-once semantics with
no distributed-transaction machinery.

Two deliberate choices in the encoder: COPY's `text` format (tab-separated,
backslash-escaped) keeps the payload debuggable — you can eyeball exactly what
went over the wire — and costs Postgres a text parse per value. The next gear
up is `FORMAT binary`, which eliminates parsing at the cost of implementing
per-type binary encoders. And the whole batch is stamped with **one** arrival
timestamp, which pins the batch to a single staging table. No batch ever
straddles a minute boundary, no matter when it commits. That one line quietly
deletes an entire class of boundary races.

## Constraints are handled separately — that's the whole trick

The staging table is created by the ingest service on first use of each
minute:

```sql
CREATE TABLE IF NOT EXISTS sensor_readings_p20260805_1432
    (LIKE sensor_readings INCLUDING DEFAULTS INCLUDING STORAGE);
```

`LIKE` clones the column definitions and NOT NULLs but — crucially — **not**
the primary key and not the indexes. While it's being loaded, this table is a
bare heap. COPY into it is a nearly pure sequential write. There is no index
to update, no uniqueness to check, no constraint to evaluate per row.

Every one of those deferred costs gets paid later, in bulk, by the
maintenance service, at a moment when the table is *detached* — a private
table no reader knows about:

- `ALTER TABLE ... ADD PRIMARY KEY (id, ingested_at)` — one bulk sort-and-build
  instead of 60 (or 60,000) incremental B-tree insertions, and uniqueness
  checked once over sorted data.
- Two `CREATE INDEX` runs matching the parent's partitioned indexes.
- The bounds `CHECK` constraint (next section) — one full-table validation scan.
- `ANALYZE` — so the planner has real statistics the instant the partition
  becomes visible, not after some future autovacuum gets around to it.

None of this touches the parent table. A reader hammering `sensor_readings`
during the index build cannot be affected by it, because as far as Postgres
lock semantics are concerned, the two tables are unrelated.

## Partition by arrival time, not event time

The schema (owned by the ingest service's Flyway migration):

```sql
CREATE TABLE sensor_readings (
    id          uuid             NOT NULL,
    device_id   text             NOT NULL,
    metric      text             NOT NULL,
    reading     double precision NOT NULL,
    recorded_at timestamptz      NOT NULL,   -- event time, from the producer
    ingested_at timestamptz      NOT NULL,   -- arrival time; THE PARTITION KEY
    CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, ingested_at)
) PARTITION BY RANGE (ingested_at);
```

Partitioning on `ingested_at` rather than the event's own `recorded_at` is a
load-bearing decision. An automated swap needs to answer "is this minute's
table *complete*?" — and with event-time partitioning it can't: an event
recorded at 14:32 can arrive at 14:37, so the 14:32 partition is forever open
to stragglers, and attaching it means either losing late data or reopening
attached partitions. Arrival time is monotonic from the writer's perspective:
once minute 14:32 has passed (plus a few seconds of grace for an in-flight
COPY), its staging table is provably done. Late events simply land in the
partition of the minute they *arrived*, with their event time preserved in
`recorded_at` for any query that cares. This is the same watermark trade-off
every stream processor makes, applied to table layout.

Note the primary key includes `ingested_at`: on a partitioned table, every
unique constraint must include the partition key (Postgres has no global
index), so `id` uniqueness is enforced per partition. For append-only
telemetry with UUID ids, that's the standard, acceptable trade.

## The swap, and exactly what it locks

Here is the maintenance service's promotion sequence, as real SQL (the Java
around it just adds timing, idempotency guards, and retries):

```sql
-- Phase 1 & 2: on the DETACHED table. Readers cannot be affected.
ALTER TABLE sensor_readings_p20260805_1432
    ADD CONSTRAINT sensor_readings_p20260805_1432_pkey PRIMARY KEY (id, ingested_at);
CREATE INDEX sensor_readings_p20260805_1432_ingested_at_idx
    ON sensor_readings_p20260805_1432 (ingested_at);
CREATE INDEX sensor_readings_p20260805_1432_device_metric_idx
    ON sensor_readings_p20260805_1432 (device_id, metric, ingested_at);
ALTER TABLE sensor_readings_p20260805_1432
    ADD CONSTRAINT sensor_readings_p20260805_1432_bounds
    CHECK (ingested_at >= '2026-08-05 14:32:00+00' AND ingested_at < '2026-08-05 14:33:00+00');
ANALYZE sensor_readings_p20260805_1432;

-- Phase 3: the only statement that touches the parent.
SET lock_timeout = '2000 ms';
ALTER TABLE sensor_readings
    ATTACH PARTITION sensor_readings_p20260805_1432
    FOR VALUES FROM ('2026-08-05 14:32:00+00') TO ('2026-08-05 14:33:00+00');
ALTER TABLE sensor_readings_p20260805_1432
    DROP CONSTRAINT sensor_readings_p20260805_1432_bounds;
RESET lock_timeout;
```

Three separate mechanisms conspire to make that `ATTACH` effectively free,
and all three are worth knowing by name:

**1. Since Postgres 12, `ATTACH PARTITION` takes only `SHARE UPDATE
EXCLUSIVE` on the parent.** Before v12 it took `ACCESS EXCLUSIVE` — a full
stop for readers. `SHARE UPDATE EXCLUSIVE` conflicts with other DDL and with
vacuum, but **not** with `SELECT`, `INSERT`, `UPDATE`, or `DELETE`. Concurrent
queries against `sensor_readings` proceed through the attach as if nothing
happened. This single version change is what turned attach-based loading from
"brief outage" into "zero downtime".

**2. The pre-validated `CHECK` constraint skips the attach-time scan.**
`ATTACH PARTITION` must prove that every row in the incoming table falls
inside the declared bounds. Without help, it proves it the hard way: scanning
the table *while holding its locks*. But if a valid `CHECK` constraint already
implies the partition bounds, Postgres skips the scan entirely. So we pay for
that scan in Phase 2, on the detached table, where it can't block anyone —
and the attach becomes a pure catalog operation. (Related: the parent has
deliberately **no DEFAULT partition** — a default partition would have to be
scanned on every attach to prove it holds no rows belonging to the incoming
range, reinstating the very problem we removed.) The `CHECK` is scaffolding;
once attached, the partition bounds enforce the same predicate, so we drop it.

**3. Pre-built matching indexes get *linked*, not built.** The parent's
indexes are partitioned indexes — templates that every partition must
implement. If the incoming table already has structurally matching indexes,
`ATTACH` just wires them into the template in the catalog. If it doesn't,
`ATTACH` builds them right there, under lock. You can verify the demo got
this right:

```sql
SELECT parent.relname AS parent_index, child.relname AS partition_index
FROM pg_inherits i
JOIN pg_class parent ON parent.oid = i.inhparent
JOIN pg_class child  ON child.oid  = i.inhrelid
WHERE parent.relkind = 'I';
```

           parent_index            |                 partition_index
    -------------------------------+--------------------------------------------------
     sensor_readings_device_metric_idx | sensor_readings_p20260805_1432_device_metric_idx
     sensor_readings_ingested_at_idx   | sensor_readings_p20260805_1432_ingested_at_idx
     sensor_readings_pkey              | sensor_readings_p20260805_1432_pkey

### lock_timeout: the non-obvious one

"Takes a weak lock" is not the same as "can't cause an outage." Lock
acquisition in Postgres is queued: if the attach's `SHARE UPDATE EXCLUSIVE`
request is waiting behind, say, a long-running `autovacuum` or a stray manual
DDL, then every request that conflicts with *the waiter* queues behind it.
A parked DDL statement can dam the whole river.

`SET lock_timeout = '2000 ms'` caps how long the attach may sit in that
queue. The service catches SQLState `55P03` (`lock_not_available`), backs
off, and retries; if all attempts lose the race, the staging table just waits
for the next scheduler tick — nothing is lost, nothing is blocked:

```java
for (int attempt = 1; attempt <= props.attachAttempts(); attempt++) {
    try {
        execute(connection, attachSql);
        return;
    } catch (SQLException e) {
        if (!"55P03".equals(e.getSQLState())) throw e;   // real failure
        sleepQuietly(200L * attempt);                     // lost the race, retry
    }
}
```

This is the discipline to copy into any production DDL you run against hot
tables, partition-related or not.

## Coordination through the catalog, not a queue

How does the maintenance service know which tables to promote? It asks the
only component that actually knows — Postgres:

```sql
SELECT c.relname
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = current_schema()
  AND c.relkind = 'r'
  AND c.relname LIKE 'sensor_readings_p%'
  AND NOT EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid)
```

A table matching the naming contract with no `pg_inherits` row *is* the work
queue: created-but-not-attached is exactly the state "waiting for promotion",
and attaching removes it from the query result atomically. The two services
share only a naming convention (one small class in `common/`), which buys real
operational properties:

- **Crash safety for free.** Every promotion phase is idempotent
  (`IF NOT EXISTS` guards, existence checks before `ADD CONSTRAINT`). If the
  maintenance service dies between building the PK and attaching, the next
  tick re-runs the same promotion and picks up where it left off. If it's
  down for an hour, the next tick drains the whole backlog in chronological
  order.
- **Horizontal safety.** Multiple maintenance instances race politely: each
  promotion takes `pg_try_advisory_lock(42001, hashtext(table_name))` and
  skips if another instance holds it.
- **Connection discipline.** The whole promotion runs on one dedicated JDBC
  connection, deliberately below JdbcClient/JPA: `SET lock_timeout`, advisory
  locks, and `RESET` are all connection-scoped, and a pool that hands each
  statement a different connection silently breaks all three. That's also why
  the swap is explicit SQL — you want to see every statement that runs, in
  order, on that connection.

One subtlety on the ingest side closes the loop: eligibility requires the
minute to be over plus a grace period (default 5s, scheduler fires at :10).
The grace covers a COPY that started at 14:32:59.9 — its rows are stamped
with the arrival timestamp taken at encode time, so the batch lands in the
14:32 table even though it commits during 14:33. A few seconds of grace and
the race window is gone.

## The read side never finds out

The payoff for all of this is what the read side *doesn't* contain. The
maintenance service maps a perfectly ordinary Spring Data JPA entity over the
parent table:

```java
@Entity
@Immutable                      // rows arrive via COPY; never dirty-check these
@Table(name = "sensor_readings")
@IdClass(SensorReadingId.class)
public class SensorReading { ... }
```

```java
public interface SensorReadingRepository extends JpaRepository<SensorReading, SensorReadingId> {
    List<SensorReading> findByDeviceIdAndMetricOrderByIngestedAtDesc(
            String deviceId, String metric, Limit limit);
    // + a native aggregate with a time-range predicate on ingested_at
}
```

Nothing in the entity, the repository, or the controllers knows partitions
exist. A row that was invisible at 14:32:59 (in a detached staging table)
is visible at 14:33:10 — already indexed, already analyzed — through the same
JPQL query, because attach changed the *catalog*, not the query. And because
every query carries a predicate on `ingested_at`, the planner prunes: run
`EXPLAIN` on the stats endpoint's SQL and old partitions simply don't appear
in the plan. Reads scale with the data you're actually asking about, not with
total retained history.

Retention is the same trick backwards, and it's where partitioning pays a
second dividend: deleting a minute of data is not a `DELETE` (heap churn,
index churn, bloat, vacuum debt) but

```sql
ALTER TABLE sensor_readings DETACH PARTITION sensor_readings_p20260805_1432 CONCURRENTLY;
DROP TABLE sensor_readings_p20260805_1432;
```

`DETACH ... CONCURRENTLY` (Postgres 14+) waits out concurrent queries instead
of blocking them, and the `DROP` afterwards removes a table no reader can see
anymore. Deletion becomes O(1) with respect to row count.

## Numbers from the demo

From an actual run of this project (60-row partitions — deliberately tiny;
the point is where the time goes, not the totals):

```
ingest-service       : COPY 4 rows -> sensor_readings_p20260805_0228 in 2361 µs (1694 rows/s)
maintenance-service  : [sensor_readings_p20260805_0228] promoted to live partition: ~60 rows |
                       pk 3 ms, indexes 4 ms, bounds-check 1 ms, analyze 1 ms,
                       attach 2 ms, drop-check 0 ms, total 16 ms
```

The line to internalize is the last one: of a 16 ms promotion, the parent
table was involved for **2 ms**, in a lock mode that doesn't conflict with
readers anyway. Scale the partition from 60 rows to 6 million and the pk /
indexes / bounds-check numbers grow with it — but they're all in the detached
phase. The attach stays a metadata operation. That's the entire thesis of the
design, visible in one log line.

## Turning it up to production

Things this demo keeps simple, and what changes at real scale:

- **Batch density.** At 1 msg/s each COPY carries a handful of rows. At real
  rates, `max.poll.records` (here 10,000) and `fetch.max.wait.ms` shape how
  many rows each COPY carries; denser is better for COPY. The code path is
  identical.
- **`FORMAT binary`** for COPY once you've measured text parsing as a cost.
  Keep text until then; you'll miss the debuggability.
- **Partition granularity.** Per-minute is for demo watchability. Real
  systems usually go hourly or daily: thousands of partitions inflate
  planning time and catalog size. Same code, different `truncatedTo`.
- **Delivery semantics.** This design is at-least-once (duplicates possible
  on redelivery, e.g. a crash after COPY commits but before offsets do).
  If duplicates matter, the batch's `(topic, partition, max-offset)` can be
  written in the same transaction as the COPY and offsets restored from the
  table on rebalance — turning the pipeline effectively exactly-once without
  Kafka transactions.
- **Unlogged staging.** `CREATE UNLOGGED TABLE` staging halves WAL on the
  load path, at the price of losing un-attached staging data on a crash
  (acceptable — Kafka replays it) and needing `ALTER TABLE ... SET LOGGED`
  before attach, which rewrites the table. Measure before buying.
- **Watch the lock retries.** The `55P03` retry warning in the maintenance
  log is your early-warning system: if attaches start losing races, something
  long-running is camping on the parent, and that's worth knowing regardless
  of the swap.

## Takeaways

1. **Bulk ingestion is a protocol question before it's a code question.**
   COPY isn't a faster INSERT; it's a different wire mode that removes
   per-row overhead entirely. Kafka's batch listener gives you the batching
   for free.
2. **Indexes and constraints are deferrable work.** Build them once per
   partition on a detached table instead of once per row on a live one, and
   `ANALYZE` before anyone can query.
3. **The swap is three mechanisms, know all three:** `SHARE UPDATE EXCLUSIVE`
   attach (PG12+), the pre-validated `CHECK` that skips the attach scan, and
   pre-built indexes that link instead of build. Miss one and the "instant"
   attach quietly does table-scale work under lock.
4. **`lock_timeout` + retry around every piece of DDL on a hot table.**
   A weak lock that queues behind a strong one is a strong lock.
5. **Partition by arrival time when an automated process needs completeness.**
   Event time is a query concern; keep it as a column.
6. **Let the catalog be the queue.** created-but-unattached is a work state
   the database maintains for you, atomically, across process crashes.
