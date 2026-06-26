#!/usr/bin/env bash
#
# test-actuator.sh
#
# Calls EVERY Actuator endpoint exposed by this demo and prints each response
# under a short descriptor, so you can eyeball the whole actuator surface at once.
#
# Tip: run ./generate-traffic.sh first so endpoints like httpexchanges, metrics
# and caches have real data to show.
#
# Usage:
#   ./test-actuator.sh
#   BASE_URL=http://localhost:8080 ./test-actuator.sh
#   ACT_USER=admin ACT_PASS=admin ./test-actuator.sh
#   MAXLINES=200 ./test-actuator.sh           # show more of each (big) response
#   INCLUDE_SHUTDOWN=1 ./test-actuator.sh      # also POST /shutdown (STOPS THE APP)
#
# JSON is pretty-printed when `jq` is installed; otherwise raw output is shown.
#
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ACT_USER="${ACT_USER:-admin}"
ACT_PASS="${ACT_PASS:-admin}"
MAXLINES="${MAXLINES:-40}"
ACT="${BASE_URL}/actuator"

have_jq() { command -v jq >/dev/null 2>&1; }

# call METHOD URL DESCRIPTION [JSON_BODY]
call() {
  local method="$1" url="$2" desc="$3" data="${4:-}"
  echo
  echo "===================================================================="
  echo ">> ${desc}"
  echo "   ${method} ${url#"$BASE_URL"}"
  echo "--------------------------------------------------------------------"

  local args=(-s -m 30 -u "${ACT_USER}:${ACT_PASS}" -X "$method" -w $'\n__HTTP__%{http_code}')
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")

  local raw status body
  raw=$(curl "${args[@]}" "$url" 2>/dev/null)
  if [[ -z "$raw" || "$raw" == *"__HTTP__000" ]]; then
    echo "   (request failed - is the app running at ${BASE_URL}?)"; return
  fi
  status="${raw##*__HTTP__}"
  body="${raw%__HTTP__*}"

  echo "HTTP ${status}"
  [[ -z "$body" ]] && return

  local rendered
  if have_jq && printf '%s' "$body" | jq . >/dev/null 2>&1; then
    rendered=$(printf '%s' "$body" | jq .)
  else
    rendered="$body"
  fi
  printf '%s\n' "$rendered" | head -n "$MAXLINES"
  [[ $(printf '%s\n' "$rendered" | wc -l) -gt "$MAXLINES" ]] && \
    echo "   ... (truncated - set MAXLINES higher to see the rest)"
}

# Heap dump is binary; save it instead of printing it.
heapdump() {
  echo
  echo "===================================================================="
  echo ">> Heap dump (binary - saved to file)"
  echo "   GET /actuator/heapdump"
  echo "--------------------------------------------------------------------"
  local out status
  out="$(mktemp -t heapdump.XXXXXX.hprof)"
  status=$(curl -s -m 120 -u "${ACT_USER}:${ACT_PASS}" -o "$out" -w '%{http_code}' "${ACT}/heapdump" 2>/dev/null)
  if [[ -z "$status" || "$status" == "000" ]]; then echo "   (request failed)"; return; fi
  echo "HTTP ${status}"
  echo "   saved $(wc -c < "$out" | tr -d ' ') bytes to ${out}"
}

echo "Testing Spring Boot 3 actuator at ${ACT} (basic-auth user: ${ACT_USER})"

# ---- Health ----
call GET "${ACT}/healthz"           "Health: aggregated status + components"
call GET "${ACT}/healthz/liveness"  "Health group: Kubernetes liveness probe"
call GET "${ACT}/healthz/readiness" "Health group: Kubernetes readiness probe"
call GET "${ACT}/healthz/business"  "Health group: custom business checks"

# ---- Info ----
call GET "${ACT}/info"              "Info: build, git, java, os + custom contributor"

# ---- Metrics ----
call GET "${ACT}/metrics"                          "Metrics: list of all meter names"
call GET "${ACT}/metrics/http.server.requests"     "Metrics: HTTP request timer (drill-down)"
call GET "${ACT}/metrics/widgets.created"          "Metrics: custom widgets.created counter"
call GET "${ACT}/prometheus"                       "Prometheus: scrape format"

# ---- Configuration & wiring ----
call GET "${ACT}/env"          "Environment: property sources (sanitized)"
call GET "${ACT}/configprops"  "@ConfigurationProperties beans (sanitized)"
call GET "${ACT}/beans"        "Beans: every Spring bean"
call GET "${ACT}/conditions"   "Conditions: auto-configuration report"
call GET "${ACT}/mappings"     "Mappings: all request mappings"

# ---- Loggers (read + runtime write) ----
call GET  "${ACT}/loggers"                       "Loggers: all configured levels"
call GET  "${ACT}/loggers/com.example.actuator"  "Loggers: a single logger"
call POST "${ACT}/loggers/com.example.actuator"  "Loggers: set level to DEBUG (write op)" '{"configuredLevel":"DEBUG"}'

# ---- Diagnostics ----
call GET "${ACT}/threaddump"    "Thread dump"
heapdump
call GET "${ACT}/httpexchanges" "HTTP exchanges: recent request/response history"
call GET "${ACT}/caches"        "Caches: managers and cache names"
call GET "${ACT}/scheduledtasks" "Scheduled tasks"
call GET "${ACT}/flyway"        "Flyway: applied database migrations"
call GET "${ACT}/auditevents"   "Audit events: security + custom events"
call GET "${ACT}/startup"       "Startup: application start-up step timings"

# ---- SBOM ----
call GET "${ACT}/sbom"             "SBOM: available SBOM ids"
call GET "${ACT}/sbom/application" "SBOM: application CycloneDX document"

# ---- Custom endpoints ----
call GET    "${ACT}/featureflags"             "Custom @Endpoint: all feature flags (read)"
call GET    "${ACT}/featureflags/beta-search" "Custom @Endpoint: single flag (read + @Selector)"
call POST   "${ACT}/featureflags/new-checkout" "Custom @Endpoint: enable a flag (write op)" '{"enabled":true}'
call DELETE "${ACT}/featureflags/beta-search" "Custom @Endpoint: delete a flag (delete op)"
call GET    "${ACT}/releasenotes"             "Custom @WebEndpoint: release notes"

# ---- Shutdown (guarded) ----
if [[ "${INCLUDE_SHUTDOWN:-0}" == "1" ]]; then
  call POST "${ACT}/shutdown" "Shutdown: gracefully stops the application"
else
  echo
  echo "===================================================================="
  echo ">> Shutdown: SKIPPED"
  echo "   POST /actuator/shutdown stops the app. Re-run with INCLUDE_SHUTDOWN=1 to call it."
fi

echo
echo "Done."
