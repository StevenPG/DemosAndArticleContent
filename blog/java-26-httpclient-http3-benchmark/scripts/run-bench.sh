#!/usr/bin/env bash
# Runs the full benchmark matrix from the blog post:
#   - sequential 1KB   (pure latency)
#   - sequential 1MB   (transfer time)
#   - concurrent 1MB   (multiplexing behavior - where HOL blocking shows up)
# for both HTTP/2 and HTTP/3.
#
# Run this once on a clean network, then again with tc/dnctl loss injection
# (see README) and compare the p95/p99 columns.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE="${BASE:-https://localhost:4443}"

for mode in sequential concurrent; do
  for payload in small.bin large.bin; do
    # concurrent small.bin adds little signal; skip to keep runs short
    [[ "$mode" == "concurrent" && "$payload" == "small.bin" ]] && continue
    for proto in HTTP_2 HTTP_3; do
      echo "--- $proto $payload $mode"
      java src/H2vsH3Bench.java "$proto" "$BASE/$payload" "$mode"
    done
  done
done
