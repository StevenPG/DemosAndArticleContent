# Relation Extension Lock Contention: the fix, and whether you need it

This document covers the one scaling wall this project deliberately does *not*
implement: relation extension lock contention between concurrent `COPY`
writers, and the sub-partitioned staging design that removes it.

It is written in the order you should actually approach the problem —
mechanism, then measurement, then fix — because **the measurements in here
say most readers do not need the fix**, and that conclusion is worth more
than the implementation.

Everything below was run against **PostgreSQL 18.4** on a 4-vCPU container
with default server settings (not the tuned `docker-compose.yml` profile), so
treat absolute numbers as directional. Test data is seeded with PG18's
built-in `uuidv7()` so the key distribution matches what the producer emits —
seeding with random v4 keys would give index builds an unrepresentative
page-locality profile. The *relative* results and the SQL mechanics were all
verified, and where a result contradicted my expectations I've said so rather
than quietly dropping it.

---

## 1. The mechanism

When a backend inserts a tuple and no existing page in the relation has room,
it must add a new page to the end of the file. Only one backend may extend a
given relation at a time, so it takes the **relation extension lock** — an
`LWLock` held for the duration of the extension.

For ordinary OLTP this is invisible. For bulk loading it is structurally
interesting, because bulk loading is *nothing but* page extension: every page
is new, none has free space, and `COPY` streams rows as fast as the client can
send them. Put N concurrent `COPY` streams into the same table and they all
queue on the same extension lock.

That is the theory, and it's the reason the concern is worth taking seriously
in a design like this one: after fixing listener concurrency (commit
`50b519c`), six threads COPY into **the same** per-minute staging table.

### What Postgres already does about it

The single-block-at-a-time behaviour that made this notorious has been
mitigated for a long time:

- **9.6** added `RelationAddExtraBlocks`: when a backend finds the extension
  lock contended, it extends by multiple blocks at once (scaled by the number
  of waiters) and puts the surplus in the free space map, so the next N
  writers find room without extending at all.
- **16** substantially reworked the bufmgr extension path (Andres Freund's
  relation-extension work), extending in bulk with less lock time held and
  fewer buffer-mapping round trips.
- **18** added asynchronous I/O (`io_method`, defaulting to `worker`), which
  changes the shape of the write path again — extension I/O can now be issued
  without blocking the backend the way it used to.

The practical consequence is that on a modern Postgres the extension lock is
much harder to hit than its reputation suggests. Which brings us to the part
that matters.

---

## 2. Measure before you build anything

Do not implement the fix in section 4 because a blog post (including this one)
told you the lock exists. Sample your wait events under real load:

```sql
-- Run repeatedly (every 50-100 ms) during peak ingestion and aggregate.
SELECT wait_event_type || ':' || wait_event AS wait, count(*)
FROM pg_stat_activity
WHERE state = 'active'
  AND backend_type = 'client backend'
  AND wait_event IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC;
```

You are looking for **`LWLock:extend`** (spelled `LWLock:extension` on
PostgreSQL 12 and earlier). If it is not in the top few rows, extension
contention is not your problem and the rest of this document is background
reading. For continuous measurement rather than sampling by hand, the
`pg_wait_sampling` extension does this properly.

A crude but effective sampler, which is what produced the numbers below:

```bash
for i in $(seq 1 60); do
  psql -t -A -c "SELECT wait_event_type||':'||wait_event
                 FROM pg_stat_activity
                 WHERE state='active' AND backend_type='client backend'
                   AND wait_event IS NOT NULL"
  sleep 0.05
done | sort | uniq -c | sort -rn
```

### What I actually measured

Two runs, identical data (1.6M rows total, 8 concurrent `COPY` streams from
files), differing only in the target: one shared table versus eight separate
tables — the best case sharding could possibly achieve, since separate tables
have entirely separate extension locks.

| 8 concurrent writers, 1.6M rows (PostgreSQL 18.4) | Wall time |
|---|---|
| → one shared table | **9,797 ms** |
| → eight separate tables | **10,511 ms** |

Sharding was **7% slower**, not faster. The sampled wait events explain why:

```
one shared table                 eight separate tables
────────────────────             ─────────────────────
 32  LWLock:WALWrite              68  LWLock:WALWrite
 20  IO:DataFileWrite             58  IO:DataFileWrite
 13  LWLock:BufferContent         22  LWLock:WALInsert
  8  LWLock:WALInsert              9  IO:WalWrite
  5  LWLock:WALBufMapping          2  IO:WalSync
```

**`LWLock:extend` does not appear in either run.** The only extension-related
event observed anywhere was a single `IO:DataFileExtend` — the *I/O* of
growing a file, not lock contention — and it showed up in the **sharded** run,
which is the opposite of the hypothesis. The bottleneck is WAL and data-file
I/O throughout. Spreading writes across eight files made the pattern less
sequential, which is where the 7% went.

The same experiment on PostgreSQL 16.13 gave the same verdict with the same
sign: 7,131 ms shared versus 7,848 ms sharded (10% slower), and no
`LWLock:extend`. A smaller PG16 run agreed too: 3 writers × 200k rows into one
table took 843 ms, into three separate tables 856 ms.

### What this means

On PostgreSQL 18 (and 16), at 8 concurrent COPY writers, on storage where I/O
is the ceiling, **relation extension contention did not materialise at all**.
That is
the honest result, it is the reason this design is documented rather than
implemented in the demo, and it should update your priors: this problem is
real, but it lives further out than folklore suggests.

The conditions under which it *does* show up, in rough order of importance:

1. **PostgreSQL 15 or older**, before the bufmgr extension rework — this is
   the big one, and it is the only condition I would treat as strong evidence
   on its own.
2. **Writer counts well beyond core count** — dozens of concurrent COPY
   streams into one relation.
3. **Storage fast enough that I/O is not the ceiling** (NVMe, or a large
   `shared_buffers` absorbing the writes), so the lock becomes the limiting
   factor instead of the disk.
4. **Narrow rows**, which means more tuples per page and a higher extension
   rate per byte written.

If you have all four, read on. If you have none, spend the effort on WAL
configuration instead — that is what my measurements say is actually in the
way.

---

## 3. A trap I walked into while testing this

Worth recording, because it produced a confidently wrong intermediate result
and it generalises to any `ATTACH PARTITION` benchmarking.

My first attempt to measure whether the bounds `CHECK` still lets `ATTACH`
skip its validation scan under sub-partitioning produced this:

| 3M rows, sub-partitioned, **no indexes built** (PG16) | Time |
|---|---|
| `ATTACH` without bounds CHECK | 13,871 ms |
| `ATTACH` with pre-validated CHECK | 11,384 ms |

Conclusion at the time: "the CHECK optimisation doesn't work through two
partition levels." That was wrong. Neither table had indexes, so **both
attaches were dominated by Postgres building three indexes over 3M rows under
lock** — mechanism #3 from the article. The validation scan was noise next to
it.

Re-run with the index set built on both tables beforehand, isolating the one
variable:

| 3M rows, sub-partitioned, **indexes pre-built** (PG18.4) | Time |
|---|---|
| `ADD CONSTRAINT … CHECK` (on the detached table) | 2,696 ms |
| `ATTACH` **without** bounds CHECK | 1,221 ms |
| `ATTACH` **with** pre-validated CHECK | **5.6 ms** |

Now it's unambiguous, and it confirms the design works through two levels: the
bounds have to be proved either way, and the CHECK moves that work off the
attach and onto the detached table where it blocks nobody. The attach itself
collapses to a 5.6 ms catalog operation — a ~220× reduction in time spent
touching the live parent. (PG16.4 gave the same shape: 1,320 ms / 1,334 ms /
1.5 ms.)

The lesson for anyone benchmarking this: **an attach has two independent
table-scale costs** — index building and bounds validation — and if you leave
one uncontrolled it will swamp the other and hand you the wrong conclusion.

---

## 4. The fix: sub-partition the staging table by writer shard

The goal is to give every writer thread its own physical heap, so there is no
shared extension lock, while still attaching the minute to the parent as a
single unit.

### 4.1 Schema

The parent gains a `writer_shard` column, and each minute-level staging table
becomes *itself* partitioned, by `LIST (writer_shard)`:

```sql
CREATE TABLE sensor_readings (
    id           uuid             NOT NULL,
    device_id    text             NOT NULL,
    metric       text             NOT NULL,
    reading      double precision NOT NULL,
    recorded_at  timestamptz      NOT NULL,
    ingested_at  timestamptz      NOT NULL,
    writer_shard smallint         NOT NULL,
    -- BOTH partition keys must appear in every unique constraint.
    CONSTRAINT sensor_readings_pkey PRIMARY KEY (id, ingested_at, writer_shard)
) PARTITION BY RANGE (ingested_at);

CREATE INDEX sensor_readings_ingested_at_idx   ON sensor_readings (ingested_at);
CREATE INDEX sensor_readings_device_metric_idx ON sensor_readings (device_id, metric, ingested_at);
```

The ingest service creates a minute like this instead of a single table:

```sql
CREATE TABLE sensor_readings_p20260805_1432 (
    LIKE sensor_readings INCLUDING DEFAULTS INCLUDING STORAGE
) PARTITION BY LIST (writer_shard);

CREATE TABLE sensor_readings_p20260805_1432_s0
    PARTITION OF sensor_readings_p20260805_1432 FOR VALUES IN (0);
CREATE TABLE sensor_readings_p20260805_1432_s1
    PARTITION OF sensor_readings_p20260805_1432 FOR VALUES IN (1);
CREATE TABLE sensor_readings_p20260805_1432_s2
    PARTITION OF sensor_readings_p20260805_1432 FOR VALUES IN (2);
-- …one leaf per writer thread
```

Note that `LIKE` on the minute-level table copies columns but no indexes, so
the leaves are still bare heaps — the property the whole staging design
depends on.

### 4.2 Writers COPY directly into their own leaf

```sql
COPY sensor_readings_p20260805_1432_s0 (id, device_id, …, writer_shard)
    FROM STDIN WITH (FORMAT text)
```

**Target the leaf, not the minute-level table.** COPY into a partitioned table
works, but every row goes through tuple routing to find its partition — pure
overhead when the writer already knows its own shard. Targeting the leaf skips
routing entirely *and* is what gives each writer its own extension lock.

### 4.3 Promotion: build at the minute level, and let it recurse

The maintenance service's phases change very little. DDL issued against the
minute-level partitioned table recurses to every leaf:

```sql
-- Recurses: creates a matching index on every leaf.
ALTER TABLE sensor_readings_p20260805_1432
    ADD CONSTRAINT sensor_readings_p20260805_1432_pkey
    PRIMARY KEY (id, ingested_at, writer_shard);
CREATE INDEX sensor_readings_p20260805_1432_ingested_at_idx
    ON sensor_readings_p20260805_1432 (ingested_at);
CREATE INDEX sensor_readings_p20260805_1432_device_metric_idx
    ON sensor_readings_p20260805_1432 (device_id, metric, ingested_at);

-- Also recurses, and is validated on each leaf.
ALTER TABLE sensor_readings_p20260805_1432
    ADD CONSTRAINT sensor_readings_p20260805_1432_bounds
    CHECK (ingested_at >= '2026-08-05 14:32:00+00'
       AND ingested_at <  '2026-08-05 14:33:00+00');

ANALYZE sensor_readings_p20260805_1432;

SET lock_timeout = '2000 ms';
ALTER TABLE sensor_readings
    ATTACH PARTITION sensor_readings_p20260805_1432
    FOR VALUES FROM ('2026-08-05 14:32:00+00') TO ('2026-08-05 14:33:00+00');
```

Verified: the CHECK lands on every leaf with `convalidated = true`, and the
attach stays metadata-only (1.5 ms for 3M rows, §3).

```
              relname              |                conname                | convalidated
-----------------------------------+---------------------------------------+--------------
 sensor_readings_p20260805_1432    | sensor_readings_p20260805_1432_bounds | t
 sensor_readings_p20260805_1432_s0 | sensor_readings_p20260805_1432_bounds | t
 sensor_readings_p20260805_1432_s1 | sensor_readings_p20260805_1432_bounds | t
 sensor_readings_p20260805_1432_s2 | sensor_readings_p20260805_1432_bounds | t
```

### 4.4 Index linkage becomes a two-level tree

The article's linkage check still works, and now shows the full hierarchy —
partitioned indexes (`relkind = 'I'`) above real indexes (`relkind = 'i'`):

```sql
SELECT p.relname AS parent_index, c.relname AS child_index, c.relkind
FROM pg_inherits i
JOIN pg_class p ON p.oid = i.inhparent
JOIN pg_class c ON c.oid = i.inhrelid
WHERE p.relkind = 'I' ORDER BY 1, 2;
```

```
 sensor_readings_ingested_at_idx                | sensor_readings_p20260805_1432_ingested_at_idx    | I
 sensor_readings_p20260805_1432_ingested_at_idx | sensor_readings_p20260805_1432_s0_ingested_at_idx | i
 sensor_readings_p20260805_1432_ingested_at_idx | sensor_readings_p20260805_1432_s1_ingested_at_idx | i
 sensor_readings_p20260805_1432_ingested_at_idx | sensor_readings_p20260805_1432_s2_ingested_at_idx | i
 …
```

Every leaf index is linked up through the minute-level partitioned index to
the parent's. Nothing is rebuilt at attach time.

---

## 5. Java changes

Modest, and mostly confined to two classes.

**`PartitionWindow` / `Partitions`** gain shard-aware naming:

```java
public String shardTableName(int shard) {
    return tableName() + "_s" + shard;
}
```

**`StagingTableManager.ensureExists`** creates the partitioned minute plus its
leaves in one memoized step. The existing `computeIfAbsent` memoization
already guarantees exactly one thread does this.

**`CopyBatchWriter`** needs a stable shard id per writer thread. The listener
thread's identity is the natural source, but do not use `Thread.getId()` — use
the Kafka partition the batch came from, which is stable across restarts and
already available on the `ConsumerRecord`:

```java
int shard = records.getFirst().partition() % shardCount;
```

A batch from one Kafka partition is handled by exactly one consumer thread, so
this yields one writer per shard with no coordination — and it survives
rebalances, because the shard follows the Kafka partition rather than the
thread that happens to own it.

The encoder gains one column. Because the batch's shard is constant, it is
encoded once per batch alongside `ingested_at`, not per row.

**`PartitionCatalog.detachedStagingTables()`** needs one adjustment that is
easy to miss: the discovery query filters `c.relkind = 'r'`, which now excludes
the minute-level partitioned tables. It must accept `'p'` as well, and must not
mistake the leaves for promotable units:

```sql
WHERE c.relkind IN ('r', 'p')
  AND c.relname LIKE 'sensor_readings_p%'
  AND c.relname !~ '_s[0-9]+$'          -- leaves are not promotable on their own
  AND NOT EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid)
```

Without that last clause the maintenance service would try to attach
individual shard leaves to the parent, which fails confusingly — the leaf's
`writer_shard` list bound has nothing to do with the parent's `ingested_at`
range bounds.

---

## 6. What it costs

Be clear-eyed; this is not free.

1. **A synthetic column in your data model.** `writer_shard` is a physical
   storage artifact with no business meaning, and it is now visible to every
   reader, every `SELECT *`, and every JPA entity.
2. **A three-column primary key.** Every unique constraint on a partitioned
   table must contain the partition key — and now there are two partition
   keys. `(id, ingested_at, writer_shard)` is wider in every index entry, and
   uniqueness of `id` is now enforced per *shard*, which is weaker still than
   per-partition.
3. **N× the catalog objects.** Six shards per minute at one-minute granularity
   is 8,640 tables per day before retention, each with three indexes. Catalog
   bloat and planning time both grow; you will want coarser partition
   granularity to compensate, which partly undoes the reason for per-minute
   swapping.
4. **More complex promotion and discovery**, per §5 — including the
   `relkind`/leaf-filtering subtlety, which is exactly the kind of thing that
   works in testing and surprises you in production.
5. **Potentially worse I/O locality**, which my measurements actually
   observed: eight files being extended round-robin is less sequential than
   one. On I/O-bound storage this can cost more than the lock does.

That list is why the demo documents this design instead of shipping it.

---

## 7. Alternatives considered

**Shard by hashing an existing column** (e.g. `device_id`) to avoid adding
`writer_shard`. Doesn't work: a writer's batch contains many devices, so each
writer would have to write into every shard, which is exactly the contention
you're trying to remove — plus tuple-routing overhead.

**One plain staging table per writer, merged before attach.** Avoids
sub-partitioning, but merging means `INSERT INTO … SELECT` over the whole
minute — reintroducing precisely the row-by-row cost the COPY path exists to
avoid.

**One plain staging table per writer, attached as separate partitions.**
Impossible: range partitions cannot overlap, and all writers share the same
minute.

**Just add more Postgres instances / shard the database.** A real answer at
sufficient scale, and a much larger conversation than this document.

**Fix the I/O instead.** Given my measurements, this is the honest first
recommendation for most people: WAL configuration, `synchronous_commit`,
checkpoint spacing, and faster storage all showed up as the actual
constraints. See the durability section of [BLOG.md](./BLOG.md).

---

## 8. Decision checklist

Implement sub-partitioned staging only if you can answer yes to most of these:

- [ ] `LWLock:extend` appears in sampled `pg_stat_activity` wait events under
      peak load, in the top few entries.
- [ ] You are on PostgreSQL 15 or older, or have many more concurrent writers
      than cores. (Measured as not needed on both 16 and 18.)
- [ ] You have already tuned WAL, checkpoints, and `synchronous_commit`, and
      confirmed I/O is not the ceiling.
- [ ] A prototype with N separate tables demonstrably beats one shared table
      on *your* hardware. (§2 gives the harness; on mine it did not.)
- [ ] You accept a synthetic column in the schema and a three-column primary
      key.

If the first box is unticked, stop. The measurements in §2 are the whole
point of this document: the fix is correct, verified, and available when you
need it — and on a modern Postgres, most workloads at "thousands of rows per
second" will never need it.

---

## Appendix: reproducing these measurements

```bash
# 1. Generate N 200k-row files with time-ordered UUIDv7 keys, matching what
#    the producer emits (v4 keys would skew any index-build measurement).
python3 - <<'EOF'
import os, time, uuid
def uuid7(ms):
    b = bytearray(ms.to_bytes(6,'big') + os.urandom(10))
    b[6] = (b[6] & 0x0F) | 0x70   # version 7
    b[8] = (b[8] & 0x3F) | 0x80   # RFC 9562 variant
    return str(uuid.UUID(bytes=bytes(b)))
base = int(time.time()*1000)
for s in range(8):
    with open(f'/tmp/s{s}.tsv','w') as f:
        for i in range(200000):
            f.write(f"{uuid7(base + i//100)}\tdevice-{i%64:03d}\ttemperature_c\t{20+i%50}.5"
                    f"\t2026-08-05T14:32:00Z\t2026-08-05T14:32:{i%60:02d}Z\t{s%3}\n")
EOF

# 2. Contended: N writers, one table.  Sharded: N writers, N tables.
#    Wrap both in the wait-event sampler from §2 and compare wall time.
```

The attach experiments in §3 use two structurally identical 3M-row
sub-partitioned tables built with
`INSERT INTO … SELECT uuidv7(), … generate_series(…)` (PG18's built-in v7
generator), with the index set built on both and the bounds `CHECK` added to
only one. Control for indexes or the result is meaningless.
