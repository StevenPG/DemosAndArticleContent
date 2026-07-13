#!/usr/bin/env bash
# The full benchmark matrix from the blog post:
#   {G1, ZGC, Serial} x {no cache, AOT cache}
# Requires app.aot (run ./scripts/train.sh first).
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f app.aot ]] || { echo "No app.aot - run ./scripts/train.sh first"; exit 1; }

for gc in "-XX:+UseG1GC" "-XX:+UseZGC" "-XX:+UseSerialGC"; do
  for cache in "" "-XX:AOTCache=app.aot"; do
    echo
    echo "=== $gc ${cache:-<no cache>}"
    ./scripts/measure-startup.sh "$gc $cache"
  done
done
