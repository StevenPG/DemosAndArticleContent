#!/usr/bin/env bash
# The full benchmark matrix from the blog post:
#   {G1, ZGC, Serial} x {no cache, AOT cache}
# Requires app.aot and app-zgc.aot (run ./scripts/train.sh first).
#
# ZGC consumes its own cache: one built under a compressed-oops collector is
# rejected by ZGC, which runs without compressed oops. See train.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

for f in app.aot app-zgc.aot; do
  [[ -f "$f" ]] || { echo "No $f - run ./scripts/train.sh first"; exit 1; }
done

for gc in "-XX:+UseG1GC" "-XX:+UseZGC" "-XX:+UseSerialGC"; do
  if [[ "$gc" == "-XX:+UseZGC" ]]; then
    cache_file=app-zgc.aot
  else
    cache_file=app.aot
  fi
  for cache in "" "-XX:AOTCache=$cache_file"; do
    echo
    echo "=== $gc ${cache:-<no cache>}"
    ./scripts/measure-startup.sh "$gc $cache"
  done
done
