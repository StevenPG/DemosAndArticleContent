#!/usr/bin/env bash
# Hit the flaky demo endpoint so the ops tools have errors, latency and logs to report.
#   ./scripts/traffic.sh [requests] [base-url]
set -euo pipefail
n="${1:-300}"
base="${2:-http://localhost:8080}"

ok=0; failed=0
for i in $(seq 1 "$n"); do
  id=$(( (RANDOM % 500) + 1 ))
  # ~2% invalid ids so there are some 400s and WARN lines too
  if (( RANDOM % 50 == 0 )); then id=0; fi
  code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/orders/$id")
  if [[ "$code" == 200 ]]; then ok=$((ok + 1)); else failed=$((failed + 1)); fi
done
echo "sent $n requests: $ok ok, $failed failed"
