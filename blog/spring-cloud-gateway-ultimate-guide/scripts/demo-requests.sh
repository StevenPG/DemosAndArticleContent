#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Exercises EVERY feature of the demo against both gateways. Run after
# ./scripts/run-demo.sh. Pass a single base URL to test just one gateway:
#
#   ./scripts/demo-requests.sh                       # both
#   ./scripts/demo-requests.sh http://localhost:8080 # reactive only
#   ./scripts/demo-requests.sh http://localhost:8090 # servlet only
# -----------------------------------------------------------------------------
set -uo pipefail

jqf() { if command -v jq >/dev/null; then jq -C "$@"; else cat; fi; }
hr()  { printf '%s\n' "----------------------------------------------------------------------"; }

suite() {
  local G="$1"
  echo
  echo "######################################################################"
  echo "#  Gateway: $G"
  echo "######################################################################"

  hr; echo "1) ROUTING + LOAD BALANCING — /inventory/whoami x6 (watch the instance flip)"
  for _ in $(seq 1 6); do curl -s "$G/inventory/whoami"; echo; done

  hr; echo "2) EDGE SECURITY — /orders with NO token => 401"
  curl -s -o /dev/null -w "   status=%{http_code}\n" "$G/orders"

  hr; echo "   Minting a demo token (dev profile only)"
  local TOKEN
  TOKEN=$(curl -s "$G/dev/token?sub=alice" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
  echo "   got a ${#TOKEN}-char JWT for subject 'alice'"

  hr; echo "3) EDGE SECURITY — /orders WITH token => 200"
  curl -s -H "Authorization: Bearer $TOKEN" "$G/orders" | jqf

  hr; echo "4) IDENTITY PROPAGATION + ANTI-SPOOF — send a FORGED X-Auth-Subject;"
  echo "   the gateway strips it and asserts the real subject from the JWT."
  curl -s -H "Authorization: Bearer $TOKEN" -H "X-Auth-Subject: HACKER" "$G/orders/echo" | jqf

  hr; echo "5) RESILIENCE / RETRY — /orders/flaky fails 2 of 3 upstream; retry hides it"
  for _ in $(seq 1 5); do curl -s -o /dev/null -w "   status=%{http_code}\n" -H "Authorization: Bearer $TOKEN" "$G/orders/flaky"; done

  hr; echo "6) RESILIENCE / CIRCUIT BREAKER — /orders/slow (3s) trips the time limiter => fallback"
  curl -s -m 8 -H "Authorization: Bearer $TOKEN" "$G/orders/slow" | jqf | sed 's/^/   /'
  curl -s -m 8 -o /dev/null -w "   -> status=%{http_code} in %{time_total}s\n" -H "Authorization: Bearer $TOKEN" "$G/orders/slow"

  hr; echo "7) RATE LIMITING — 20 rapid calls; burst passes, the rest get 429"
  printf '   '
  for _ in $(seq 1 20); do curl -s -o /dev/null -w "%{http_code} " -H "Authorization: Bearer $TOKEN" "$G/orders/o-1001"; done
  echo

  hr; echo "8) RESPONSE HEADERS added by the gateway"
  sleep 2
  curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "$G/orders/o-1001" \
    | grep -iE 'x-gateway|x-correlation|x-ratelimit' | sed 's/^/   /'

  hr; echo "9) OBSERVABILITY — live routes from the actuator gateway endpoint"
  curl -s "$G/actuator/gateway/routes" | jqf '.[].route_id' 2>/dev/null || curl -s "$G/actuator/gateway/routes" | jqf
}

if [[ $# -ge 1 ]]; then
  suite "$1"
else
  suite "http://localhost:8080"   # reactive
  suite "http://localhost:8090"   # servlet
fi
echo
echo "Done."
