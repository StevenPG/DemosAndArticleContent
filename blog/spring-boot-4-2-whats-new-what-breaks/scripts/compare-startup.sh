#!/usr/bin/env bash
# Builds the identical baseline app on Spring Boot 4.1 and 4.2, then starts each
# jar RUNS times (alternating) and prints the "Started ... in" line from every run
# plus the medians.
#
# Usage (from the project root): ./scripts/compare-startup.sh [RUNS]
set -euo pipefail

cd "$(dirname "$0")/.."
RUNS="${1:-12}"
MVN="${MVN:-./mvnw}"
RESULTS="$(mktemp)"
LOG="$(mktemp)"

for v in boot-4.1 boot-4.2; do
  "$MVN" -q -DskipTests -f "startup-baseline/$v/pom.xml" package
done

for r in $(seq 1 "$RUNS"); do
  for v in boot-4.1 boot-4.2; do
    java -jar "startup-baseline/$v/target/boot42-0.0.1-SNAPSHOT.jar" --server.port=0 >"$LOG" 2>&1 &
    pid=$!
    for _ in $(seq 1 100); do
      grep -q "Started Boot42Application" "$LOG" && break
      sleep 0.2
    done
    echo "$r $v $(grep -o 'Started Boot42Application in [0-9.]* seconds (process running for [0-9.]*)' "$LOG")" | tee -a "$RESULTS"
    kill "$pid"
    wait "$pid" 2>/dev/null || true
  done
done

python3 - "$RESULTS" <<'EOF'
import re, statistics, sys
runs = {}
for line in open(sys.argv[1]):
    m = re.match(r"\d+ (\S+) .* in ([\d.]+) seconds \(process running for ([\d.]+)\)", line)
    if m:
        runs.setdefault(m[1], []).append((float(m[2]), float(m[3])))
for version, values in runs.items():
    started = statistics.median(a for a, _ in values)
    process = statistics.median(b for _, b in values)
    print(f"{version}: started median {started:.3f}s, process median {process:.3f}s (n={len(values)})")
EOF
