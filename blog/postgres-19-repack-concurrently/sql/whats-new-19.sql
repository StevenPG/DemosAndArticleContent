-- Quick checks of what PostgreSQL 19 actually ships, run against the beta.
--   docker exec -i pg19-repack psql -U postgres -X -f /sql/whats-new-19.sql
\pset pager off
SELECT version();

\echo '== Defaults that changed or are new'
SELECT name, setting, boot_val FROM pg_settings
WHERE name IN ('jit', 'max_locks_per_transaction', 'wal_level', 'max_repack_replication_slots',
               'autovacuum_max_parallel_workers', 'compute_query_id')
ORDER BY name;

\echo '== New monitoring views'
SELECT viewname FROM pg_views
WHERE viewname IN ('pg_stat_lock', 'pg_stat_recovery', 'pg_stat_autovacuum_scores', 'pg_dsm_registry_allocations')
ORDER BY 1;

\echo '== REPACK accepts the old VACUUM FULL / CLUSTER jobs'
CREATE TEMP TABLE probe (id int PRIMARY KEY, v int);
INSERT INTO probe SELECT g, g FROM generate_series(1, 1000) g;
REPACK probe;
REPACK probe USING INDEX probe_pkey;
VACUUM (FULL) probe;
CLUSTER probe USING probe_pkey;

\echo '== Things some previews said were in 19 (expect errors on the beta)'
\set ON_ERROR_STOP off
SELECT v % 3 AS bucket, count(*) FROM probe GROUP BY ALL;
CREATE TEMP TABLE rate (room int, valid daterange, price int);
UPDATE rate FOR PORTION OF valid FROM '2026-06-01' TO '2026-09-01' SET price = 150;
\echo '== SQL/PGQ (reverted before release)'
CREATE PROPERTY GRAPH g VERTEX TABLES (probe);
