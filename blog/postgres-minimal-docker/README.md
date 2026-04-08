# PostgreSQL on a Budget: 140 MB Docker Container

A production-ready Docker configuration for running PostgreSQL 18 inside a
hard memory limit of **140 MB**, suitable for a 500 MB VPS shared with other
services.  Includes a custom `postgresql.conf` with annotated tuning decisions
and scripts to measure memory usage and run load tests.

---

## Project layout

```
postgres-minimal-docker/
├── docker-compose.yml          # Container definition with 140 MB memory limit
├── .env.example                # Copy to .env and set credentials
├── postgres/
│   └── postgresql.conf         # Tuned config – every setting is annotated
└── scripts/
    ├── connection-test.sh      # Verify connectivity and print active settings
    ├── memory-check.sh         # Live memory report + Postgres internals
    └── bench.sh                # pgbench read-only and read-write benchmarks
```

---

## Quick start

```bash
# 1. Copy and edit credentials
cp .env.example .env

# 2. Start the container
docker compose up -d

# 3. Verify it started cleanly
docker compose logs postgres

# 4. Check active settings
./scripts/connection-test.sh
```

The container enforces a **140 MB hard limit** via Docker's `deploy.resources`
constraints.  If PostgreSQL exceeds this the kernel OOM-killer will terminate
it, so the config below keeps well within that ceiling.

---

## Memory budget breakdown

| Component | Approx. size | Notes |
|---|---|---|
| `shared_buffers` | 32 MB | PostgreSQL's internal page cache |
| `wal_buffers` | 4 MB | In-memory WAL before fsync |
| Postmaster + bg workers | ~15 MB | 2 background workers configured |
| 10 active connections | ~50 MB | ~5 MB each (process + stack) |
| OS overhead inside container | ~10 MB | libc, kernel page tables, etc. |
| **Total (typical)** | **~111 MB** | Leaves ~29 MB headroom |

With all 25 connections active the worst case approaches ~130 MB.  On a real
workload most connections are idle most of the time, so typical usage sits
well below the limit.

---

## Key tuning decisions

### `shared_buffers = 32MB`

The canonical recommendation is 25% of RAM.  On a 140 MB budget that is
35 MB; 32 MB is a round number that still leaves headroom for the OS page
cache, which PostgreSQL also benefits from.

### `max_connections = 25`

Every connection is a forked OS process costing 5–10 MB.  If your application
needs more than ~20 simultaneous connections, put **PgBouncer** in front of
Postgres rather than raising this number.  PgBouncer multiplexes many
application connections onto a small number of server connections and adds
only a few MB of overhead.

### `work_mem = 2MB`

Used per-sort and per-hash-join operation, not per connection.  A complex
query can use it several times in parallel.  The safe rule is:

```
work_mem = (available RAM - shared_buffers) / (max_connections * 3)
           ≈ (108MB - 32MB) / (25 * 3) ≈ 1MB
```

2 MB is a reasonable value for simple OLTP workloads.  Increase it for
reporting queries that sort large data sets, but keep an eye on memory.

### `max_worker_processes = 2` and parallel workers

Parallel query workers are expensive.  Each spawned worker allocates its own
`work_mem` and process memory.  With 2 workers configured, Postgres will not
try to parallelise queries, keeping memory predictable.

### `autovacuum_max_workers = 1`

Autovacuum is essential – never disable it.  One worker is sufficient for a
low-write workload on a small instance.

---

## Scripts

### `./scripts/connection-test.sh`

Connects to the database and prints the active values of the most
memory-relevant settings so you can confirm the config file was loaded.

```
$ ./scripts/connection-test.sh
Connecting to localhost:5432 as appuser…

        version
------------------------
 PostgreSQL 18.x ...

     name          | setting | unit | ...
-------------------+---------+------+----
 effective_cache_size | 16384 | 8kB  | ...
 max_connections      | 25    |      | ...
 shared_buffers       | 4096  | 8kB  | ...
 work_mem             | 2048  | kB   | ...
 ...
```

### `./scripts/memory-check.sh`

Queries `docker stats` for the live RSS of the container and compares it to
the 140 MB limit.  Also connects to Postgres to show database size and active
connection count.

```bash
./scripts/memory-check.sh           # single snapshot
./scripts/memory-check.sh --watch   # refresh every 2 s
```

Example output:

```
14:23:01               87.4 MB  /  140 MB limit  (62%)

  PostgreSQL internals:
   db_size | active_connections | shared_buffers | work_mem
  ---------+--------------------+----------------+----------
   8473 kB |                  2 | 33554432B      | 2097152B
```

### `./scripts/bench.sh [scale] [clients] [duration_s]`

Wraps `pgbench` in two passes: a **read-only** (SELECT-heavy) run followed by
a **read-write** (TPC-B-like) run.  Defaults are conservative for a 500 MB
host:

```bash
./scripts/bench.sh          # scale=10, 5 clients, 30 s each
./scripts/bench.sh 5 3 60   # smaller data set, fewer clients, longer run
```

**Requires** `pgbench` on the host:

```bash
# Debian / Ubuntu
sudo apt install postgresql-client

# macOS
brew install libpq && brew link --force libpq
```

---

## Running alongside other applications

On a 500 MB VPS, a rough allocation might look like:

| Service | Memory budget |
|---|---|
| OS + kernel | ~80 MB |
| PostgreSQL (this config) | ~110 MB typical / 140 MB limit |
| Nginx | ~20 MB |
| Application (e.g. Node/Go/Java) | ~150–200 MB |
| Buffer / headroom | ~60 MB |

Keep an eye on the host's swap usage.  Even a little swap activity under a
database workload causes latency spikes; if you see swapping, reduce
`max_connections` further or add a connection pooler.

---

## Requirements

- Docker >= 24 with Compose V2 (`docker compose`)
- `pgbench` / `psql` on the host (only for the benchmark and test scripts)
