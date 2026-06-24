#!/usr/bin/env bash
#
# generate-traffic.sh
#
# Drives the demo's business API so that the Actuator endpoints have real data to
# report: http.server.requests timers, httpexchanges history, cache hits/misses,
# the widgets.* custom metrics, audit events, and distributed-trace spans.
#
# Run this BEFORE test-actuator.sh for the most interesting output.
#
# Usage:
#   ./generate-traffic.sh
#   BASE_URL=http://localhost:8080 ./generate-traffic.sh
#
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

req() {
  local method="$1" path="$2" data="${3:-}"
  local args=(-s -m 20 -o /dev/null -w '%{http_code}' -X "$method")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  local code
  code=$(curl "${args[@]}" "${BASE_URL}${path}" 2>/dev/null)
  if [[ -z "$code" || "$code" == "000" ]]; then
    echo "  [FAILED]   ${method} ${path}  - is the app running at ${BASE_URL}?"
  else
    echo "  [HTTP ${code}]  ${method} ${path}"
  fi
}

echo "Generating traffic against ${BASE_URL} ..."

# A simple read (populates greeting.read observation + request metrics)
req GET /api/greeting

# Create a few widgets (widgets.created counter, WIDGET_CREATED audit events)
for i in 1 2 3; do
  req POST /api/widgets "{\"name\":\"Widget-${i}\",\"color\":\"blue\"}"
done

# List + read by id twice (first call = cache miss, second = cache hit)
req GET /api/widgets
req GET /api/widgets/1
req GET /api/widgets/1

# A validation failure (HTTP 400) to add variety to the request metrics
req POST /api/widgets '{"name":"","color":""}'

# A lookup that does not exist (error path)
req GET /api/widgets/999999

# Delete one (WIDGET_DELETED audit event + cache eviction)
req DELETE /api/widgets/2

echo
echo "Done. Now run ./test-actuator.sh to inspect the actuator endpoints."
