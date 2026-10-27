#!/usr/bin/env bash
# Batch warmup benchmark (warmup-cpu): N iterations of the same work in one JVM.
#
# Usage: TAG=batch MODE=adsb ITERATIONS=30 REPS=5 ./scripts/batch.sh <config>...
source "$(dirname "$0")/lib.sh"
TAG="${TAG:-batch}"
MODE="${MODE:-adsb}"
REPS="${REPS:-5}"
export ITERATIONS="${ITERATIONS:-30}"

for cfg in "$@"; do verify_config warmup-cpu "$cfg"; done

for r in $(seq 1 "$REPS"); do
  for cfg in "$@"; do
    out="$RESULTS/batch-$TAG-$MODE-${cfg//:/_}-r$r.csv"
    start=$(date +%s%N)
    # shellcheck disable=SC2046
    taskset -c "$APP_CPUS" $(jvm_cmd warmup-cpu "$cfg" "$MODE") >"$out"
    wall=$(( ($(date +%s%N) - start) / 1000000 ))
    checksum=$(grep -oE 'CHECKSUM=[0-9-]+' "$out")
    sed -i '/CHECKSUM=/d' "$out"
    echo "$TAG,$MODE,$cfg,$r,$wall,$checksum" | tee -a "$RESULTS/batch-summary.csv"
  done
done
