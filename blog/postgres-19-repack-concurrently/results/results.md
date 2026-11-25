# REPACK benchmark - results

PostgreSQL `19.0 (Debian 19.0-2.pgdg13+1)`, 2,000,000 rows inserted then 70% deleted, 8 pgbench clients writing throughout. Docker saw 2 CPUs.

| Method | Duration s | Heap MB before -> after | Indexes MB before -> after | Writer tps before | Writer tps during | Seconds with zero commits | Max writer latency ms (baseline) |
|---|---|---|---|---|---|---|---|
| `VACUUM FULL` | 0.6 | 281 -> 98 | 103 -> 38 | 14,018 | 6,560 | 0 | 507 (17) |
| `REPACK` | 0.6 | 281 -> 98 | 103 -> 38 | 13,512 | 6,066 | 0 | 484 (22) |
| `REPACK (CONCURRENTLY)` | 1.2 | 281 -> 99 | 103 -> 38 | 13,159 | 10,023 | 0 | 65 (7) |
