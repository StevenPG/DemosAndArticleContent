#!/usr/bin/env bash
# memory-check.sh – Report live memory usage of the postgres-minimal container
#                   and compare it against the 140MB limit.
#
# Usage:
#   ./scripts/memory-check.sh [--watch]
#
#   --watch   Refresh every 2 seconds (Ctrl-C to stop).

set -euo pipefail

CONTAINER="${CONTAINER_NAME:-postgres-minimal}"
LIMIT_MB=140
WARN_PCT=80   # print a warning when usage exceeds this % of the limit

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

check_once() {
  # docker stats outputs bytes with a unit suffix (MiB / GiB).
  # --no-stream gives a single snapshot.
  RAW=$(docker stats --no-stream --format "{{.MemUsage}}" "$CONTAINER" 2>/dev/null || true)

  if [[ -z "$RAW" ]]; then
    echo "Container '${CONTAINER}' not found or not running."
    echo "Start it with:  docker compose up -d"
    return 1
  fi

  # Parse the "used / limit" string, e.g. "87.4MiB / 140MiB"
  USED_STR=$(echo "$RAW" | awk -F' / ' '{print $1}')
  LIMIT_STR=$(echo "$RAW" | awk -F' / ' '{print $2}')

  # Convert to MB (handle MiB and GiB)
  to_mb() {
    local val="$1"
    if echo "$val" | grep -qi GiB; then
      echo "$val" | sed 's/[Gg][Ii][Bb]//g' | awk '{printf "%.1f", $1 * 1024}'
    else
      echo "$val" | sed 's/[Mm][Ii][Bb]//g' | awk '{printf "%.1f", $1}'
    fi
  }

  USED_MB=$(to_mb "$USED_STR")
  PCT=$(echo "$USED_MB $LIMIT_MB" | awk '{printf "%d", ($1/$2)*100}')

  # Pick colour based on percentage
  if   [ "$PCT" -ge 95 ]; then COLOR="$RED"
  elif [ "$PCT" -ge "$WARN_PCT" ]; then COLOR="$YELLOW"
  else COLOR="$GREEN"
  fi

  printf "%-22s %s%6s MB%s  /  %s MB limit  (%s%d%%%s)\n" \
    "$(date '+%H:%M:%S')" \
    "$COLOR" "$USED_MB" "$NC" \
    "$LIMIT_MB" \
    "$COLOR" "$PCT" "$NC"

  if [ "$PCT" -ge "$WARN_PCT" ]; then
    echo -e "  ${YELLOW}Warning: container is using >=${WARN_PCT}% of its memory limit.${NC}"
  fi

  # Also pull shared_buffers and connections from Postgres itself
  echo ""
  echo "  PostgreSQL internals:"
  docker exec "$CONTAINER" psql \
    -U "${POSTGRES_USER:-appuser}" \
    -d "${POSTGRES_DB:-appdb}" \
    -c "
      SELECT
        pg_size_pretty(pg_database_size(current_database())) AS db_size,
        (SELECT count(*) FROM pg_stat_activity)              AS active_connections,
        (SELECT setting || 'B' FROM pg_settings WHERE name = 'shared_buffers')
                                                             AS shared_buffers,
        (SELECT setting || 'B' FROM pg_settings WHERE name = 'work_mem')
                                                             AS work_mem;
    " 2>/dev/null | grep -v "^$" || true
  echo ""
}

if [[ "${1:-}" == "--watch" ]]; then
  echo "Watching '${CONTAINER}' memory every 2s — Ctrl-C to stop."
  echo ""
  while true; do
    check_once
    sleep 2
  done
else
  check_once
fi
