# SQL demo output

Captured against `postgres:19beta4` via `docker compose up -d`.

## sql/whats-new-19.sql

```
Pager usage is off.
                                                          version                                                          
---------------------------------------------------------------------------------------------------------------------------
 PostgreSQL 19beta4 (Debian 19~beta4-1.pgdg13+1) on x86_64-pc-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit
(1 row)

== Defaults that changed or are new
              name               | setting | boot_val 
---------------------------------+---------+----------
 autovacuum_max_parallel_workers | 0       | 0
 compute_query_id                | auto    | auto
 jit                             | off     | off
 max_locks_per_transaction       | 128     | 128
 max_repack_replication_slots    | 5       | 5
 wal_level                       | replica | replica
(6 rows)

== New monitoring views
          viewname           
-----------------------------
 pg_dsm_registry_allocations
 pg_stat_autovacuum_scores
 pg_stat_lock
 pg_stat_recovery
(4 rows)

== REPACK accepts the old VACUUM FULL / CLUSTER jobs
CREATE TABLE
INSERT 0 1000
REPACK
REPACK
VACUUM
CLUSTER
== Things some previews said were in 19 (expect errors on the beta)
CREATE TABLE
== SQL/PGQ (reverted before release)
psql:/sql/whats-new-19.sql:27: ERROR:  syntax error at or near ";"
LINE 1: SELECT v % 3 AS bucket, count(*) FROM probe GROUP BY ALL;
                                                                ^
psql:/sql/whats-new-19.sql:29: ERROR:  syntax error at or near "FOR"
LINE 1: UPDATE rate FOR PORTION OF valid FROM '2026-06-01' TO '2026-...
                    ^
psql:/sql/whats-new-19.sql:31: ERROR:  syntax error at or near "PROPERTY"
LINE 1: CREATE PROPERTY GRAPH g VERTEX TABLES (probe);
               ^
```

## sql/plan-advice.sql

```
Pager usage is off.
DROP TABLE
CREATE TABLE
CREATE TABLE
INSERT 0 500
INSERT 0 200000
CREATE INDEX
ANALYZE

== 1. What did the planner choose, and what advice reproduces it?
LOAD
                             QUERY PLAN                              
---------------------------------------------------------------------
 Finalize GroupAggregate
   Group Key: a.tail
   ->  Gather Merge
         Workers Planned: 1
         ->  Sort
               Sort Key: a.tail
               ->  Partial HashAggregate
                     Group Key: a.tail
                     ->  Hash Join
                           Hash Cond: (f.aircraft_id = a.id)
                           ->  Parallel Seq Scan on flight f
                           ->  Hash
                                 ->  Seq Scan on aircraft a
                                       Filter: (type = 'E175'::text)
 Generated Plan Advice:
   JOIN_ORDER(f a)
   HASH_JOIN(a)
   SEQ_SCAN(f a)
   GATHER_MERGE((f a))
(19 rows)


== 2. Ask for a different plan for this session only, and read the feedback
SET
                        QUERY PLAN                        
----------------------------------------------------------
 HashAggregate
   Group Key: a.tail
   ->  Nested Loop
         ->  Seq Scan on aircraft a
               Filter: (type = 'E175'::text)
         ->  Index Scan using flight_aircraft on flight f
               Index Cond: (aircraft_id = a.id)
 Supplied Plan Advice:
   INDEX_SCAN(f flight_aircraft) /* matched */
   JOIN_ORDER(a f) /* matched */
   NESTED_LOOP_PLAIN(f) /* matched */
(11 rows)


== 3. Advice that cannot be followed says so instead of silently doing nothing
SET
                             QUERY PLAN                              
---------------------------------------------------------------------
 Finalize GroupAggregate
   Group Key: a.tail
   ->  Gather Merge
         Workers Planned: 1
         ->  Sort
               Sort Key: a.tail
               ->  Partial HashAggregate
                     Group Key: a.tail
                     ->  Hash Join
                           Hash Cond: (f.aircraft_id = a.id)
                           ->  Parallel Seq Scan on flight f
                           ->  Hash
                                 ->  Seq Scan on aircraft a
                                       Filter: (type = 'E175'::text)
 Supplied Plan Advice:
   INDEX_SCAN(f no_such_index) /* matched, inapplicable, failed */
   HASH_JOIN(zzz) /* not matched */
(17 rows)

RESET

== 4. Pin advice to the query id with pg_stash_advice - applies to every session that opts into the stash
CREATE EXTENSION
 pg_drop_advice_stash 
----------------------
 
(1 row)

 pg_create_advice_stash 
------------------------
 
(1 row)

DO
 stash_name |       query_id       |                           advice_string                            
------------+----------------------+--------------------------------------------------------------------
 prod       | -7823247077965662498 | JOIN_ORDER(a f) NESTED_LOOP_PLAIN(f) INDEX_SCAN(f flight_aircraft)
(1 row)

SET
                        QUERY PLAN                        
----------------------------------------------------------
 HashAggregate
   Group Key: a.tail
   ->  Nested Loop
         ->  Seq Scan on aircraft a
               Filter: (type = 'A320'::text)
         ->  Index Scan using flight_aircraft on flight f
               Index Cond: (aircraft_id = a.id)
 Supplied Plan Advice:
   INDEX_SCAN(f flight_aircraft) /* matched */
   JOIN_ORDER(a f) /* matched */
   NESTED_LOOP_PLAIN(f) /* matched */
(11 rows)

psql:/sql/plan-advice.sql:35: NOTICE:  extension "pg_stash_advice" already exists, skipping
```
