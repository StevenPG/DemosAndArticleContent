#!/usr/bin/env bash
# Warmup curve for spring-rest: launch the server, hammer it from the moment it is
# listening, and record throughput + latency for every second since JVM launch.
#
# Usage: TAG=curve MIX=full DURATION=120 REPS=3 ./scripts/curve.sh <config>...
# Repetitions are interleaved (A B C A B C ...) so slow drift on the host spreads evenly
# across configs instead of penalising whichever ran last.
source "$(dirname "$0")/lib.sh"
TAG="${TAG:-curve}"
MIX="${MIX:-full}"
DURATION="${DURATION:-120}"
REPS="${REPS:-3}"
CONC="${CONC:-8}"

for cfg in "$@"; do verify_config spring-rest "$cfg"; done

for r in $(seq 1 "$REPS"); do
  for cfg in "$@"; do
    out="$RESULTS/curve-$TAG-${cfg//:/_}-r$r.csv"
    launch=$(date +%s%N)
    # shellcheck disable=SC2046
    taskset -c "$APP_CPUS" $(jvm_cmd spring-rest "$cfg") >/dev/null 2>&1 &
    pid=$!
    stats=$(taskset -c "$LOAD_CPUS" "$LOADGEN" -mix "$MIX" -duration "${DURATION}s" -c "$CONC" \
              -launch-ns "$launch" -out "$out" 2>&1)
    rss=$(awk '/VmRSS/{print $2}' "/proc/$pid/status" 2>/dev/null || echo 0)
    stop_app "$pid"
    echo "$TAG,$cfg,$r,$MIX,$stats,rss_kb=$rss" | tr ' ' ',' | tee -a "$RESULTS/curve-summary.csv"
    sleep 2   # let the port and page cache settle
  done
done
