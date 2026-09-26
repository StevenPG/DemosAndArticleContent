-- pg_plan_advice + pg_stash_advice: capture a plan, pin it by query id, see the feedback.
--   docker exec -i pg19-repack psql -U postgres -X -f /sql/plan-advice.sql
\set ON_ERROR_STOP on
\pset pager off

DROP TABLE IF EXISTS flight, aircraft;
CREATE TABLE aircraft (id int PRIMARY KEY, tail text NOT NULL, type text NOT NULL);
CREATE TABLE flight (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, aircraft_id int NOT NULL, block_minutes int NOT NULL);
INSERT INTO aircraft SELECT g, 'N' || g || 'SP', (ARRAY['A320','B738','E175','CRJ9'])[1 + g % 4] FROM generate_series(1, 500) g;
INSERT INTO flight (aircraft_id, block_minutes) SELECT 1 + (g % 500), 45 + (g % 300) FROM generate_series(1, 200000) g;
CREATE INDEX flight_aircraft ON flight (aircraft_id);
ANALYZE aircraft, flight;

\echo
\echo '== 1. What did the planner choose, and what advice reproduces it?'
LOAD 'pg_plan_advice';
EXPLAIN (COSTS OFF, PLAN_ADVICE)
SELECT a.tail, count(*) FROM flight f JOIN aircraft a ON a.id = f.aircraft_id WHERE a.type = 'E175' GROUP BY a.tail;

\echo
\echo '== 2. Ask for a different plan for this session only, and read the feedback'
SET pg_plan_advice.advice = 'JOIN_ORDER(a f) NESTED_LOOP_PLAIN(f) INDEX_SCAN(f flight_aircraft)';
EXPLAIN (COSTS OFF)
SELECT a.tail, count(*) FROM flight f JOIN aircraft a ON a.id = f.aircraft_id WHERE a.type = 'E175' GROUP BY a.tail;

\echo
\echo '== 3. Advice that cannot be followed says so instead of silently doing nothing'
SET pg_plan_advice.advice = 'INDEX_SCAN(f no_such_index) HASH_JOIN(zzz)';
EXPLAIN (COSTS OFF)
SELECT a.tail, count(*) FROM flight f JOIN aircraft a ON a.id = f.aircraft_id WHERE a.type = 'E175' GROUP BY a.tail;
RESET pg_plan_advice.advice;

\echo
\echo '== 4. Pin advice to the query id with pg_stash_advice - applies to every session that opts into the stash'
CREATE EXTENSION IF NOT EXISTS pg_stash_advice;
SELECT pg_drop_advice_stash('prod') FROM pg_get_advice_stashes() WHERE stash_name = 'prod';
SELECT pg_create_advice_stash('prod');
DO $$
DECLARE
    plan json;
BEGIN
    EXECUTE $q$EXPLAIN (VERBOSE, FORMAT JSON)
        SELECT a.tail, count(*) FROM flight f JOIN aircraft a ON a.id = f.aircraft_id WHERE a.type = 'E175' GROUP BY a.tail$q$
        INTO plan;
    PERFORM pg_set_stashed_advice('prod', (plan -> 0 ->> 'Query Identifier')::bigint,
        'JOIN_ORDER(a f) NESTED_LOOP_PLAIN(f) INDEX_SCAN(f flight_aircraft)');
END $$;
SELECT * FROM pg_get_advice_stash_contents('prod');

SET pg_stash_advice.stash_name = 'prod';
-- Different literal, same query id: the stashed advice still applies.
EXPLAIN (COSTS OFF)
SELECT a.tail, count(*) FROM flight f JOIN aircraft a ON a.id = f.aircraft_id WHERE a.type = 'A320' GROUP BY a.tail;
