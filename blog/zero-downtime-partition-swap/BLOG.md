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
attaches it, zero-downtime, once a minute.

Every number in this post comes from actually running it at 2,000 events/sec
against **PostgreSQL 18**, which produces ~120,000-row partitions — enough that
the costs are real rather than rounding error.

The complete runnable project is in this folder ([README](./README.md) for
the commands). Here's why it's built the way it is.

## The shape of the system

```
                     ┌────────────────── ingest-service ─────────────────────┐
  producer ────────► │  Kafka topic ──► 6 listener threads                   │
                     │                    │                                  │
                     │                    └─► COPY FROM STDIN (streamed)     │
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

Batches come from Kafka for free. `spring.kafka.listener.type: batch` hands
the listener everything one `poll()` returned — up to `max.poll.records` —
as a single `List`, and commits the offsets only after the listener returns.
One poll, one COPY, one offset commit. A single COPY is atomic (all rows or
none), so a crash mid-batch means Kafka redelivers the whole batch and the
staging table never holds a partial one: clean at-least-once semantics with
no distributed-transaction machinery.

### Stream the payload; don't build it

The obvious implementation renders the batch to a `String` and hands it over:

```java
// Don't do this.
String payload = encodeBatch(events);
copyManager.copyIn(copySql, new StringReader(payload));
```

At 10,000 rows that's a multi-megabyte `char[]` — two bytes per character —
which then gets encoded to UTF-8 on the way out. Two full copies of every
batch, on every listener thread, straight into the young generation. The
allocation profile alone undoes a good part of why you chose COPY.

The version in this project encodes directly to UTF-8 bytes in a buffer the
thread owns and reuses, and drains it to the server every 64 KB through
pgjdbc's lower-level `CopyIn` handle:

```java
CopyIn copyIn = copyManager.copyIn(copySql);
try {
    for (SensorReadingEvent event : events) {
        encoder.appendRow(event, ingestedAtBytes);
        if (encoder.length() >= FLUSH_THRESHOLD) {
            copyIn.writeToCopy(encoder.buffer(), 0, encoder.length());
            encoder.reset();
        }
    }
    if (encoder.length() > 0) {
        copyIn.writeToCopy(encoder.buffer(), 0, encoder.length());
    }
    return copyIn.endCopy();
} catch (SQLException | RuntimeException e) {
    if (copyIn.isActive()) {
        copyIn.cancelCopy();   // else the pooled connection stays in COPY mode
    }
    throw e;
}
```

Peak footprint is now flat — about 72 KB per writer — regardless of batch
size. Two details worth stealing:

- **`cancelCopy()` on failure.** An abandoned COPY leaves the connection stuck
  in COPY mode, and the pool hands that broken connection to the next batch.
  This is the kind of bug that shows up as a cascade of unrelated failures
  ten minutes after the real problem.
- **UUIDs written from their two `long`s** rather than via
  `UUID.toString()`, which would allocate a String per row. Twenty lines,
  and it removes an allocation from the innermost loop.

Text format (tab-separated, backslash-escaped) keeps the wire payload
debuggable, at the cost of a text parse per value server-side. The next gear
up is `FORMAT binary`, which eliminates parsing at the cost of per-type binary
encoders. Keep text until a profile tells you otherwise; you'll miss being
able to read it.

### One thread is not a write path

This is the mistake that silently caps most COPY-based ingesters, and it's one
line of configuration:

```yaml
spring:
  kafka:
    listener:
      type: batch
      concurrency: 6      # one consumer thread per topic partition
```

The default is **1**. Six topic partitions and a single listener thread means
one COPY at a time no matter how much hardware you have. With `concurrency: 6`
each thread owns partitions, encodes into its own buffer, and runs its own
independent COPY.

Two things must line up with it, or the parallelism is fictional:

```yaml
    hikari:
      maximum-pool-size: 8      # ≥ concurrency, or writers serialize on checkout
      minimum-idle: 8
```

```yaml
      properties:
        max.poll.interval.ms: 300000
```

`max.poll.interval.ms` has to comfortably exceed the worst-case COPY of a
`max.poll.records`-sized batch. If it doesn't, a heavy batch trips a rebalance,
the rebalance redelivers the batch, the redelivery makes the next batch
heavier, and the pipeline oscillates itself to death. Sizing the connection
pool below the listener concurrency is the same class of error, just quieter:
the threads exist, they simply queue on connection checkout.

## Partition by arrival time, not event time

The schema (owned by the ingest service's Flyway migration):

```sql
CREATE TABLE sensor_readings (
    id          uuid             NOT NULL,   -- UUIDv7, time-ordered
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

The whole batch is stamped with **one** arrival timestamp, taken once at
encode time. That pins the batch to a single staging table: no batch ever
straddles a minute boundary, no matter when it commits. One line, and an
entire class of boundary race disappears.

Two more schema notes. The primary key includes `ingested_at` because on a
partitioned table every unique constraint must contain the partition key
(Postgres has no global index) — so `id` uniqueness is enforced per partition,
which is the standard and acceptable trade for append-only telemetry. And the
ids are **UUIDv7**, not v4: 48 bits of millisecond timestamp followed by
randomness, so they sort in creation order. In this design the key is
bulk-built rather than incrementally maintained, which mutes the difference —
but the resulting index is physically correlated with the heap, and under the
incremental-insert pattern this whole architecture avoids, random v4 keys
scatter writes across every leaf page and inflate WAL through full-page
writes. Shipping v4 keys in a project about write performance would argue
against its own thesis.

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

## Durability you can afford to lose

The single largest throughput knob for a bulk loader is usually not in your
application at all:

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: SET synchronous_commit = off
```

With `synchronous_commit = off`, COPY returns without waiting for the WAL
fsync. Measured A/B on the same hardware at 4,000 events/sec:

| PostgreSQL 18.4 | COPY p50 | COPY p99 |
|---|---|---|
| `synchronous_commit = on` | 6.26–6.78 ms | 22–46 ms |
| `synchronous_commit = off` | 5.49 ms | 21–27 ms |

Worth being precise about how much this is worth, because it moved between
versions. On PostgreSQL 16.13 the same A/B was dramatic — p50 5.73 → 4.44 ms
and p99 ~109 → ~18 ms, roughly 6× at the tail. On 18.4 the win is real but far
smaller: about 15% at p50 and a tighter, less spiky tail. PostgreSQL 18's
asynchronous I/O (`io_method`, defaulting to `worker`) reshapes the commit path
enough that the stock configuration is already much better behaved.

The lesson generalises past this one setting: **re-measure your tuning after a
major version upgrade.** A knob that bought 6× on one release can quietly
become a 15% knob on the next, and the reasoning that justified the trade-off
deserves rechecking along with the number.

Two things make this defensible rather than reckless. First, **it is not
`fsync = off`** — the database remains crash-safe and will never corrupt;
only the commit *acknowledgement* becomes asynchronous, so a crash loses at
most a few hundred milliseconds of committed transactions. Second, and more
importantly, **Kafka is the source of truth**. The offsets for anything lost
were never committed either, so the consumer replays exactly those records on
restart. We are trading durability we can reconstruct for latency we can't.

Note that this is set on the ingest service's pool only. The maintenance
service keeps full durability: its DDL isn't replayable from anywhere.

The server side needs help too, and the defaults are tuned for a small shared
machine, not for sustained ingestion:

```yaml
command:
  - postgres
  - -c
  - max_wal_size=8GB              # stop checkpointing constantly under load
  - -c
  - checkpoint_timeout=15min
  - -c
  - checkpoint_completion_target=0.9
  - -c
  - wal_compression=lz4           # shrink full-page images after each checkpoint
  - -c
  - shared_buffers=1GB
  - -c
  - maintenance_work_mem=512MB    # the swap's bulk index builds
  - -c
  - log_lock_waits=on             # so the lock_timeout story is observable
```

Checkpoints are the dominant source of write stalls during ingestion. Letting
WAL grow much larger before forcing one, and spreading the flush across 90% of
the interval, converts a periodic I/O cliff into a background trickle.

## When a batch can't be written

The default behaviour of a Kafka listener that throws is worth stating
plainly, because it's the failure mode most ingestion demos ship with: the
offsets are never committed, Kafka redelivers the same batch immediately, and
the loop spins as fast as the CPU allows. **One malformed record silently
wedges a partition forever.**

The policy here has two halves, and the split matters more than either half.

**Transient failures retry forever.** The database being down is not the
batch's fault. COPY failures are classified by SQLState *class* — the
portable, documented part of the error code:

```java
return switch (stateClass) {
    // 22 data exception, 23 integrity violation, 42 syntax/access rule
    case "22", "23", "42" -> new PoisonBatchException(message, e);
    // 08 connection, 40 rollback/deadlock, 53 resources, 57 shutdown, …
    default               -> new TransientIngestException(message, e);
};
```

Note the direction of the default: **anything unrecognised is transient**. A
stalled partition is visible, alertable, and drains itself when the database
comes back. A batch dead-lettered because Postgres was mid-failover is silent
data loss. Given an unknown error, block loudly rather than discard quietly.

**Un-processable data is dead-lettered immediately**, because retrying it can
only ever block the partition. Getting this right for a *batch* listener has
three parts that are each easy to miss:

1. **`ErrorHandlingDeserializer` signals failure with a null value.** Without
   it, a malformed record throws inside `poll()`, where no error handler can
   catch it. With it, the bad record arrives as `null`.
2. **The listener must therefore take `ConsumerRecord`s, not values.** A
   listener declared `List<SensorReadingEvent>` cannot distinguish that null
   and will just throw `NullPointerException` deep in the write path — which
   the handler then treats as a generic, retryable failure. Same wedge, more
   confusing stack trace.
3. **`BatchListenerFailedException` carries the failing record's index**, which
   is what lets the handler commit everything before it, dead-letter exactly
   that one record, and redeliver the rest.

```java
for (int i = 0; i < records.size(); i++) {
    SensorReadingEvent event = records.get(i).value();
    if (event == null) {
        DeserializationException cause = SerializationUtils.getExceptionFromHeader(
                records.get(i), SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, HEADER_LOG);
        throw new BatchListenerFailedException("routing to dead-letter topic", cause, i);
    }
    events.add(event);
}
```

And one more trap, which I walked straight into while building this. Because
the retry policy above is deliberately *unlimited*, the poison record inherits
it: it retries forever and never reaches the recoverer, so the partition
wedges anyway — just more slowly and with less to show for it. The exceptions
that identify un-processable data have to be excluded explicitly:

```java
handler.addNotRetryableExceptions(
        PoisonBatchException.class,
        BatchListenerFailedException.class);
```

Dead letters are published as **raw bytes**, not as `SensorReadingEvent`: a
record that failed to deserialize has no object form, and the original bytes
are exactly what an operator needs to see.

## Observability, and why the log line had to go

The first version of this project logged a line per COPY. It reads beautifully
in a demo and is actively harmful in production: at a few thousand batches a
minute, the appender's synchronized write becomes a contention point shared by
every listener thread, and the formatting cost lands on the hot path. Worse,
it doesn't actually answer the question you care about.

Per-batch logging moved to `DEBUG`. Steady state is Micrometer meters plus one
aggregated line per window:

```
ingest: 20000 rows in 100 batches (2000 rows/s, 200 rows/batch) | copy p50 3.92 ms, p99 15.71 ms
```

A p99 COPY latency and a rows/sec rate tell you whether ingestion is healthy.
No volume of individual log lines does. The same meters are on
`/actuator/prometheus` as histograms, so the p99 is queryable rather than
merely printed.

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

Nothing in the entity, the repository, or the controllers knows partitions
exist. A row that was invisible at 14:32:59 (in a detached staging table) is
visible at 14:33:10 — already indexed, already analyzed — through the same
JPQL query, because attach changed the *catalog*, not the query.

And because every query carries a predicate on `ingested_at`, the planner
prunes:

```
 Aggregate
   ->  Append
         Subplans Removed: 2
         ->  Index Only Scan using sensor_readings_p20260805_0324_ingested_at_idx
               Index Cond: (ingested_at >= (now() - '00:01:00'::interval))
```

`Subplans Removed: 2` is runtime pruning doing its job. Reads scale with the
data you're actually asking about, not with total retained history.

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

On the ingest side, the staging-table DDL is memoized through
`computeIfAbsent` so exactly one thread creates each minute's table. Letting
all six threads fire `CREATE TABLE IF NOT EXISTS` at the minute boundary
mostly works, but there's a genuine race: `IF NOT EXISTS` checks the catalog
*before* taking the lock, so simultaneous creators can collide with a
duplicate-key error on `pg_class` rather than politely no-opping.

## Numbers from the demo

Running at 2,000 events/sec, which fills each per-minute partition with about
120,000 rows:

```
ingest: 20000 rows in 100 batches (2000 rows/s, 200 rows/batch) | copy p50 3.92 ms, p99 15.71 ms

[sensor_readings_p20260805_0422] promoted to live partition: ~120000 rows |
    pk 139 ms, indexes 266 ms, bounds-check 13 ms, analyze 85 ms,
    attach 2 ms, drop-check 1 ms, total 516 ms
```

The line to internalize is the last one. Promoting 120,000 rows into the live
table costs **516 ms of work, of which the parent table is involved for
2 ms** — in a lock mode that doesn't conflict with readers anyway. The other
514 ms happens on a table no reader can see.

The ratio is the point, and it holds as the partition grows. At 3M rows in a
standalone test, the same attach took **5.6 ms** while the bounds validation it
skipped cost 1.2 s — so a 25× larger partition moved the work done off the live
table up by roughly 25×, and the work done on it barely at all.

Scale the partition to 12 million rows and the pk / indexes / bounds-check
numbers grow with it. They're all in the detached phase. The attach stays a
metadata operation. That's the entire thesis of the design, visible in one log
line.

## What breaks next

Things this project doesn't do, and where the next walls are:

**Relation extension lock contention — probably not, and I have the numbers.**
Six threads COPY into the same staging table, and the folklore says they will
queue on the relation extension lock (the lock Postgres takes to add a page to
a heap). Bulk loading is nothing but page extension, so this is a legitimate
worry, and the fix — sub-partitioning each minute by writer shard so every
thread gets its own heap — is a real design.

I benchmarked it before recommending it, and the result went the other way. On
PostgreSQL 18.4, 8 concurrent COPY writers, 1.6M rows: one shared table took
9,797 ms and eight separate tables took **10,511 ms** — sharding was 7%
*slower*. Sampled wait events show why: `LWLock:WALWrite` and
`IO:DataFileWrite` dominate, and **`LWLock:extend` never appears at all**. The
only extension-related event seen anywhere was a single `IO:DataFileExtend`,
and it appeared in the *sharded* run. PostgreSQL 16.13 agreed with the same
sign (7,131 ms versus 7,848 ms). Postgres has mitigated single-block extension
since 9.6, reworked the path in 16, and added async I/O in 18; spreading writes
across eight files just made the I/O less sequential.

So: measure before you build. Sample `pg_stat_activity` for `LWLock:extend`
under peak load, and only reach for sharded staging if it's actually near the
top. The full design, the verified SQL, the Java changes, the costs, and the
measurement harness are written up in
[RELATION_EXTENSION_LOCK_FIX.md](./RELATION_EXTENSION_LOCK_FIX.md) — including
a benchmarking trap that gave me a confidently wrong answer on the first
attempt.

**Exactly-once.** This design is at-least-once: a crash after the COPY commits
but before offsets do will redeliver and duplicate. If duplicates matter,
write the batch's `(topic, partition, max-offset)` in the same transaction as
the COPY and restore offsets from that table on rebalance. That makes the
pipeline effectively exactly-once without Kafka transactions.

**Postgres-generated UUIDv7.** PostgreSQL 18 added `uuidv7()` (alongside
`uuidv4()`, `uuid_extract_version()` and `uuid_extract_timestamp()`). For a
table whose rows are born in the database, that is now the right way to get a
time-ordered key. This design deliberately doesn't use it: the id identifies
the event across Kafka, so it has to exist in the producer long before any row
reaches Postgres, and COPY always supplies the column so a `DEFAULT` would
never fire. It is the correct tool pointed at a different problem — but it is
exactly what to reach for when seeding test or benchmark data, where random v4
keys would give index builds an unrepresentative locality profile.

**`FORMAT binary`** once you've measured text parsing as a real cost, and
**unlogged staging tables** (`CREATE UNLOGGED TABLE`) to halve WAL on the load
path — at the price of needing `ALTER TABLE … SET LOGGED` before attach, which
rewrites the table. Measure both before buying.

**Schema evolution needs a plan.** This is the operational sharp edge the
design adds, and it is worth thinking about before you need it. The COPY
statement names its columns explicitly, and staging tables are cloned from the
parent with `LIKE`. Add a column to the parent and the two can disagree for
exactly as long as one staging table outlives the deploy: an old ingest
instance COPYing its old column list into a table cloned from the new parent
is fine (the new column takes its default), but a new instance COPYing a new
column into a staging table cloned *before* the migration fails with SQLState
42703 — which the classifier correctly routes to the dead-letter topic, so a
careless deploy costs you a minute of data rather than a stalled partition.

The safe sequence is the ordinary online-migration discipline, and partitioning
does not change it: add the column as nullable with a default first, deploy
readers, deploy writers, and only then make it `NOT NULL`. What partitioning
*does* change is the window: a per-minute staging table means the disagreement
can only last about a minute, which is a genuine argument for fine granularity
that has nothing to do with swap latency.

**Partition granularity.** Per-minute is for demo watchability. Real systems
usually go hourly or daily; thousands of partitions inflate planning time and
catalog size. Same code, different `truncatedTo`.

**Watch the lock-retry warnings.** The `55P03` retry line in the maintenance
log is an early-warning system: if attaches start losing races, something
long-running is camping on the parent, and that's worth knowing regardless of
the swap.

## Takeaways

1. **Bulk ingestion is a protocol question before it's a code question.**
   COPY isn't a faster INSERT; it's a different wire mode that removes
   per-row overhead entirely — then stream into it instead of materializing
   the payload, or you hand the allocator back what you saved.
2. **Parallelism is three settings that must agree**: listener concurrency,
   connection pool size, and `max.poll.interval.ms`. Any one of them wrong
   and the other two are decoration.
3. **Indexes and constraints are deferrable work.** Build them once per
   partition on a detached table instead of once per row on a live one, and
   `ANALYZE` before anyone can query.
4. **The swap is three mechanisms, know all three:** `SHARE UPDATE EXCLUSIVE`
   attach (PG12+), the pre-validated `CHECK` that skips the attach scan, and
   pre-built indexes that link instead of build. Miss one and the "instant"
   attach quietly does table-scale work under lock.
5. **`lock_timeout` + retry around every piece of DDL on a hot table.**
   A weak lock that queues behind a strong one is a strong lock.
6. **Relax durability exactly where you can rebuild it.** Kafka-replayable
   writes are the textbook case for `synchronous_commit = off`; DDL is not.
   Then re-measure it after every major version upgrade — this knob was worth
   6× at p99 on Postgres 16 and about 15% at p50 on Postgres 18.
7. **Classify failures, don't just retry them.** Retry the database being
   down forever; dead-letter un-processable data immediately. Defaulting an
   unknown error to "transient" turns a mystery into a stalled partition
   instead of into missing rows.
8. **Partition by arrival time when an automated process needs completeness.**
   Event time is a query concern; keep it as a column.
9. **Let the catalog be the queue.** created-but-unattached is a work state
   the database maintains for you, atomically, across process crashes.
