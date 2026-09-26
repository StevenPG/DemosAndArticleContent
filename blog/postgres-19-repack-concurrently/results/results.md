# REPACK benchmark - results

PostgreSQL `19beta4 (Debian 19~beta4-1.pgdg13+1)`, 2,000,000 rows inserted then 70% deleted, 8 pgbench clients writing throughout. Docker saw 2 CPUs.

| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |
|---|---|---|---|---|---|---|---|
| `VACUUM FULL` | 0.8 | 281 -> 162 | 103 -> 87 | 8,654 | 8,138 | 0 | 706 (22) |
| `REPACK` | 0.7 | 281 -> 191 | 103 -> 119 | 12,915 | 10,980 | 0 | 585 (56) |
| `REPACK (CONCURRENTLY)` | 1.2 | 281 -> 189 | 103 -> 118 | 13,728 | 10,384 | 0 | 38 (30) |

_Recorded before the harness measured size right after the command: the "after" sizes above were taken at the end of the writer window and include every row the writer inserted in the meantime, so they understate how much the rewrite reclaimed._
