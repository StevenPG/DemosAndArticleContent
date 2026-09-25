# REPACK benchmark - results

PostgreSQL `19beta4 (Debian 19~beta4-1.pgdg13+1)`, 8,000,000 rows inserted then 70% deleted, 8 pgbench clients writing throughout. Docker saw 4 CPUs.

| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |
|---|---|---|---|---|---|---|---|
| `VACUUM FULL` | 5.7 | 1124 -> 416 | 412 -> 215 | 5,496 | 382 | 4 | 5,585 (69) |
| `REPACK` | 3.4 | 1124 -> 421 | 412 -> 216 | 4,290 | 1,098 | 2 | 3,198 (29) |
| `REPACK (CONCURRENTLY)` | 5.0 | 1124 -> 420 | 412 -> 217 | 5,447 | 3,623 | 0 | 108 (16) |
