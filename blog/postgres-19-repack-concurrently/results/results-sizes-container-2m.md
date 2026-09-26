# REPACK benchmark - results

PostgreSQL `19beta4 (Debian 19~beta4-1.pgdg13+1)`, 2,000,000 rows inserted then 70% deleted, 8 pgbench clients writing throughout. Docker saw 4 CPUs.

| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |
|---|---|---|---|---|---|---|---|
| `VACUUM FULL` | 1.7 | 281 -> 91 | 103 -> 34 | 4,860 | 2,719 | 1 | 1,545 (14) |
| `REPACK` | 1.6 | 281 -> 92 | 103 -> 34 | 5,684 | 2,903 | 1 | 1,461 (10) |
| `REPACK (CONCURRENTLY)` | 1.9 | 281 -> 93 | 103 -> 34 | 5,229 | 4,721 | 0 | 23 (16) |
