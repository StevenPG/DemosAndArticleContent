# PostgreSQL 19: REPACK (CONCURRENTLY), Measured Against a Live Writer

PostgreSQL 19 adds [`REPACK`](https://www.postgresql.org/docs/19/sql-repack.html), one command that replaces
`VACUUM FULL` and `CLUSTER`. More importantly, it adds `REPACK (CONCURRENTLY)`: rewrite a bloated table
and give the space back to the OS **without blocking reads and writes**, until then the reason people
installed `pg_repack`.

This project measures what each way of rewriting a table costs the *application* writing to it, and
includes working demos of the other PostgreSQL 19 feature I'd actually use: `pg_plan_advice` +
`pg_stash_advice`.

Accompanies the post **[PostgreSQL 19's REPACK CONCURRENTLY vs VACUUM FULL: What Your Application Feels](https://stevenpg.com/posts/postgres-19-repack-concurrently/)**.

## Layout

```
compose.yaml                 postgres:19beta4 with pg_stash_advice preloaded
sql/setup-bloated.sql        2M rows, delete 70%, plain VACUUM: a table that is 3x the size it needs
sql/workload.pgbench         the live writer: insert + indexed read + update per transaction
sql/plan-advice.sql          pg_plan_advice / pg_stash_advice walkthrough
sql/whats-new-19.sql         checks of new defaults, views, and a couple of features that did NOT ship
scripts/repack_bench.py      runs VACUUM FULL, REPACK, REPACK (CONCURRENTLY) under load
results/                     results.md + raw.json (container run), sql-output.md
```

## Run it

Requires Docker and Python 3.11+. psql and pgbench run inside the container.

```bash
docker compose up -d
python3 scripts/repack_bench.py run                         # 2M rows, 8 writers, all three methods
python3 scripts/repack_bench.py run --rows 10000000 --clients 16 --max-method-seconds 300
python3 scripts/repack_bench.py report

docker exec -i pg19-repack psql -U postgres -X -f /sql/plan-advice.sql
docker exec -i pg19-repack psql -U postgres -X -f /sql/whats-new-19.sql
```

## How the benchmark works

For each method, the harness:

1. Rebuilds the same bloated table: 2M rows inserted, 70% deleted, then a plain `VACUUM` that marks
   the space reusable but can't return it.
2. Starts a pgbench writer (8 clients by default). Each transaction inserts a row, reads the latest
   10 positions for one aircraft through the index, and updates one surviving row.
3. After a 10 s warm-up, runs the method, then keeps the writer going until its fixed window ends.
4. Reads pgbench's **per-second aggregate log** (`--aggregate-interval=1`) and reports, for the seconds
   the method was running:
   - average writer throughput, compared with the 10 s before
   - **seconds in which no writer transaction committed at all**, which is what an `ACCESS EXCLUSIVE` lock
     looks like from the application
   - the **worst single transaction latency**, compared with the baseline's worst
5. Records heap and index size before and after.

pgbench buffers the aggregate log and loses the buffer if killed, so the writer runs for a fixed
window (`--warmup` + `--max-method-seconds` + `--cooldown`) and exits on its own. On bigger tables,
raise `--max-method-seconds`. The harness warns if the method outlived the window.

## Findings from the beta (deterministic, see `results/sql-output.md`)

- **`REPACK (CONCURRENTLY)` works with the default `wal_level=replica`.** It captures concurrent
  changes with logical decoding through its own slots, capped by `max_repack_replication_slots`
  (default 5). You don't need to switch the cluster to `wal_level=logical`.
- `VACUUM FULL` and `CLUSTER` still work alongside `REPACK` and `REPACK ... USING INDEX`.
- **Parallel autovacuum is opt-in.** `autovacuum_max_parallel_workers` defaults to `0`.
- **JIT is now off by default** (`jit = off`). If you run large analytical queries, turn it back on
  deliberately.
- `max_locks_per_transaction` default doubled to 128, because lock-table sizing changed. If you set it
  explicitly before, double your value to keep the same capacity.
- New views exist: `pg_stat_lock`, `pg_stat_recovery`, `pg_stat_autovacuum_scores`,
  `pg_dsm_registry_allocations`.
- **Not in 19**, despite several preview articles: `GROUP BY ALL` and `UPDATE ... FOR PORTION OF` are both
  syntax errors on 19beta4.

## Restrictions on CONCURRENTLY

From the docs. `REPACK (CONCURRENTLY)` refuses a table that:

- isn't a plain `heap` table, or is `UNLOGGED`, partitioned, a system catalog or TOAST table
- has **no primary key and no index-based replica identity**

It also can't run inside a transaction block. A partitioned table means repacking each partition.

## Results

Two runs on a shared 4-core x86_64 cloud container, 8 writers each. **Read them as shape only.** The blog post
numbers come from a re-run on dedicated hardware.

- `results/results.md` / `raw.json`: 8M rows (heap 1,124 MB → ~420 MB)
- `results/results-2m.md` / `raw-2m.json`: 2M rows (heap 281 MB → ~129 MB)

The 8M-row shape:

| Method | Duration s | Seconds with zero commits | Worst writer transaction (baseline) | Writer tps before → during |
|---|---:|---:|---:|---:|
| `VACUUM FULL` | 5.7 | 4 | 5,585 ms (14) | 5,496 → 382 |
| `REPACK` | 3.4 | 2 | 3,198 ms (29) | 4,290 → 1,098 |
| `REPACK (CONCURRENTLY)` | 5.0 | **0** | **108 ms** (16) | 5,447 → 3,623 |

The locking methods block the writer for about the whole rewrite: the worst transaction takes as long as the
command. `CONCURRENTLY` never stops the writer. Its worst transaction was ~108 ms, which is the final swap, and
throughput dropped by about a third while it copied.
