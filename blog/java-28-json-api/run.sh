#!/usr/bin/env bash
# Runs every demo in order with the source launcher. Needs JDK 28 (EA build 21+).
#   ./run.sh            all demos
#   ./run.sh Numbers    one demo
set -euo pipefail
cd "$(dirname "$0")"

feature=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
if [[ "${feature%%.*}" -lt 28 ]]; then
  echo "Found Java $feature; jdk.incubator.json needs JDK 28+. Grab an EA build from https://jdk.java.net/28/" >&2
  exit 1
fi

demos=(Forecast AircraftStates ThreadDumpSchema GenerateGeoJson Strictness Numbers RecordBinding)
[[ $# -gt 0 ]] && demos=("$@")

for demo in "${demos[@]}"; do
  printf '\n===== %s =====\n' "$demo"
  # The incubator module must be added explicitly; the launcher passes it to javac too.
  java --add-modules jdk.incubator.json "src/$demo.java"
done
