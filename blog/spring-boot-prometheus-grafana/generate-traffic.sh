#!/usr/bin/env bash
# Generates continuous traffic against the demo app so the dashboard has
# something to show. Ctrl-C to stop.
set -euo pipefail

echo "Generating traffic against localhost:8080 (Ctrl-C to stop)..."
while true; do
  curl -s -o /dev/null -X POST localhost:8080/checkout || true
  curl -s -o /dev/null localhost:8080/products || true
  sleep 0.2
done
