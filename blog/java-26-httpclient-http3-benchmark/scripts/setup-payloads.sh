#!/usr/bin/env bash
# Generates the two benchmark payloads:
#   www/small.bin - 1KB  (latency-dominated workload)
#   www/large.bin - 1MB  (throughput + loss-recovery workload)
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p www
head -c 1024 /dev/urandom > www/small.bin
head -c 1048576 /dev/urandom > www/large.bin
echo "Payloads written: $(ls -lh www | tail -n +2 | awk '{print $9, $5}')"
