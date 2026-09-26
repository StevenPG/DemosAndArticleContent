# REPACK benchmark - results

PostgreSQL `19beta4 (Debian 19~beta4-1.pgdg13+1)`, 2,000,000 rows inserted then 70% deleted, 8 pgbench clients writing throughout. Docker saw 4 CPUs.

| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |
|---|---|---|---|---|---|---|---|
| `VACUUM FULL` | 1.5 | 281 -> 130 | 103 -> 63 | 5,586 | 1,672 | 0 | 1,396 (14) |
| `REPACK` | 1.5 | 281 -> 128 | 103 -> 63 | 5,381 | 1,932 | 0 | 1,304 (25) |
| `REPACK (CONCURRENTLY)` | 1.6 | 281 -> 129 | 103 -> 63 | 5,505 | 4,148 | 0 | 26 (23) |
