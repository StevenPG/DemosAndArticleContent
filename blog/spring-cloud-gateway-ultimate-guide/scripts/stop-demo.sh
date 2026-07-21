#!/usr/bin/env bash
# Tears down everything started by run-demo.sh.
set -uo pipefail
cd "$(dirname "$0")/.."

DEMO_DIR=".demo"

if [[ -f "$DEMO_DIR/pids" ]]; then
  echo "==> Stopping JVMs"
  while read -r pid; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null && echo "    killed $pid"
  done < "$DEMO_DIR/pids"
  rm -f "$DEMO_DIR/pids"
else
  echo "==> No PID file; nothing to kill"
fi

echo "==> Stopping Redis (docker compose)"
docker compose down

echo "Done."
