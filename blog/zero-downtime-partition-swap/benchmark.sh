#!/usr/bin/env bash
#
# Head-to-head write-path benchmark:
#   COPY into detached staging partitions   (ingest-service)
#   vs Spring Data JPA saveAll(), naive     (jpa-baseline-service, profile=naive)
#   vs Spring Data JPA saveAll(), tuned     (jpa-baseline-service, profile=tuned)
#
# Method
# ------
# Preload the topic with a fixed message set while every consumer is stopped,
# then let each implementation drain it from offset 0 under its own consumer
# group. Each run therefore sees byte-identical input, and nothing is timed
# against a live producer whose rate could drift.
#
# Progress is read from each app's own Micrometer counter rather than by
# counting rows in the database, so the measurement never contends with the
# workload and never depends on autovacuum statistics.
#
#   ./benchmark.sh [MESSAGE_COUNT]      # default 300000
#
# Requires: Postgres 18 and Kafka reachable (docker compose up -d), plus curl.
set -uo pipefail

MESSAGES="${1:-300000}"
RESULTS="${RESULTS:-benchmark-results.txt}"
PGHOST="${PGHOST:-localhost}" PGUSER="${PGUSER:-demo}" PGDATABASE="${PGDATABASE:-partition_swap}"
export PGPASSWORD="${PGPASSWORD:-demo}"
TIMEOUT_S="${TIMEOUT_S:-1800}"

record()  { printf '%s\n' "$*" | tee -a "$RESULTS"; }
banner()  { printf '\n=== %s ===\n' "$*" | tee -a "$RESULTS"; }
metric()  { curl -s "http://localhost:$1/actuator/prometheus" 2>/dev/null \
              | awk -v m="$2" '$1==m {print int($2); found=1} END {if(!found) print 0}'; }
stop_all() {
  ps -eo pid,args | grep -E "[I]ngestServiceApplication|[J]paBaselineApplication" \
    | awk '{print $1}' | while read -r p; do kill "$p" 2>/dev/null; done
  sleep 5
}

# $1 label  $2 module  $3 args  $4 port  $5 rows metric  $6 drain metric  $7 reset SQL
run_case() {
  local label="$1" module="$2" args="$3" port="$4" meter="$5" drain_meter="$6" reset_sql="$7"
  banner "$label"
  # Each implementation must start from an empty target. Without this the
  # second JPA run replays the same UUIDs into a populated table and dies on
  # the primary key.
  psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAqc "$reset_sql" >/dev/null 2>&1
  local log="/tmp/bench-${module}-$(echo "$label" | tr ' ' '_').log"
  ./gradlew -q ":$module:bootRun" --args="$args" >"$log" 2>&1 &

  # Wait for the app to come up and report its first rows.
  local rows=0 waited=0
  while [ "$rows" -eq 0 ] && [ "$waited" -lt 120 ]; do
    sleep 2; waited=$((waited + 2)); rows=$(metric "$port" "$meter")
  done
  if [ "$rows" -eq 0 ]; then
    record "FAILED       : app never reported rows; last errors from $log"
    grep -iE "APPLICATION FAILED|Caused by:" "$log" | head -3 | while read -r l; do record "  $l"; done
    stop_all
    return 1
  fi
  local start; start=$(date +%s%N)
  local start_rows=$rows

  # Drain until the counter reaches the preloaded total.
  local elapsed_s=0
  while [ "$rows" -lt "$MESSAGES" ] && [ "$elapsed_s" -lt "$TIMEOUT_S" ]; do
    sleep 2; rows=$(metric "$port" "$meter")
    elapsed_s=$(( ($(date +%s%N) - start) / 1000000000 ))
  done

  # Timing comes from the app's own first-write-to-last-write window, not from
  # this loop: a COPY drain finishes in seconds, and a 2s poll interval would
  # be most of the measurement. The loop only decides when to stop waiting.
  local drain_s; drain_s=$(curl -s "http://localhost:$port/actuator/prometheus" \
    | awk -v m="$drain_meter" '$1==m {print $2}')
  local drain_ms; drain_ms=$(awk -v s="${drain_s:-0}" 'BEGIN{printf "%d", s*1000}')

  record "rows drained : $rows of $MESSAGES"
  record "drain window : ${drain_ms} ms (in-app, first write to last)"
  [ "${drain_ms:-0}" -gt 0 ] && record "throughput   : $(( rows * 1000 / drain_ms )) rows/s"
  [ "$rows" -lt "$MESSAGES" ] && record "NOTE         : timed out after ${TIMEOUT_S}s (did not drain)"
  grep -hoE "(ingest|baseline): .*" "$log" | tail -2 | while read -r l; do record "  $l"; done
  record "log          : $log"
  stop_all
}

: > "$RESULTS"
record "benchmark $(date -u +%FT%TZ)"
record "postgres  $(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAqc 'SHOW server_version')"
record "messages  $MESSAGES"
stop_all

banner "preload: $MESSAGES messages (all consumers stopped)"
./gradlew -q :ingest-service:bootRun --args="\
--demo.consumer.enabled=false \
--demo.producer.messages-per-second=20000 \
--demo.producer.total-messages=$MESSAGES" > /tmp/preload.log 2>&1 &
for _ in $(seq 1 300); do grep -q "preload complete" /tmp/preload.log && break; sleep 2; done
grep -h "preload complete" /tmp/preload.log | tail -1 | while read -r l; do record "  $l"; done
stop_all

DROP_STAGING="DO \$\$ DECLARE t text; BEGIN FOR t IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'sensor_readings_p%' LOOP EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', t); END LOOP; END \$\$;"

run_case "COPY into staging partitions" \
  "ingest-service" \
  "--demo.producer.enabled=false --spring.kafka.consumer.group-id=bench-copy" \
  8080 "ingest_rows_written_total" "ingest_drain_seconds" "$DROP_STAGING"

run_case "Spring Data JPA saveAll - NAIVE" \
  "jpa-baseline-service" \
  "--spring.profiles.active=naive --spring.kafka.consumer.group-id=bench-jpa-naive" \
  8082 "baseline_rows_written_total" "baseline_drain_seconds" \
  "TRUNCATE sensor_readings_jpa"

run_case "Spring Data JPA saveAll - TUNED" \
  "jpa-baseline-service" \
  "--spring.profiles.active=tuned --spring.kafka.consumer.group-id=bench-jpa-tuned" \
  8082 "baseline_rows_written_total" "baseline_drain_seconds" \
  "TRUNCATE sensor_readings_jpa"

banner "results in $RESULTS"
