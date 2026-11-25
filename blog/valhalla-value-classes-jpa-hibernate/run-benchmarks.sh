#!/usr/bin/env bash
# Runs the hydration benchmark in three JVM configurations and prints the summary.
# Usage: JDK28=/path/to/jdk-28.jdk/Contents/Home ./run-benchmarks.sh [results-dir]
set -euo pipefail
cd "$(dirname "$0")"
: "${JDK28:?set JDK28 to your JDK 28 EA home}"
R="${1:-build/bench-results}"
mkdir -p "$R"
run() {
  ./gradlew --no-daemon -Porg.gradle.java.installations.paths="$JDK28" \
    -Porg.gradle.java.installations.auto-download=false benchmark "$@"
}
run > "$R/default-1.log" 2>&1
run > "$R/default-2.log" 2>&1
run "-Pbench.flags=-XX:+UnlockDiagnosticVMOptions -XX:-UseFieldFlattening" > "$R/no-field-flattening.log" 2>&1
for f in "$R"/*.log; do
  echo "## $f"
  grep -A3 "=== HYDRATION" "$f" || true
  grep -A2 "=== IN-MEMORY" "$f" || true
  grep BUILD "$f" || true
done
