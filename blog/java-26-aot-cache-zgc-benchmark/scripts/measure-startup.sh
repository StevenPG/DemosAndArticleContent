#!/usr/bin/env bash
# Measures startup as TIME TO READINESS: from `java` invocation until
# /actuator/health/readiness first returns 200, polled every 5ms.
#
# Usage: ./scripts/measure-startup.sh "<jvm flags>"
#   e.g. ./scripts/measure-startup.sh "-XX:+UseZGC -XX:AOTCache=app.aot"
#
# 11 runs; the first is discarded (page-cache priming); prints each run,
# then median/min/max of the remaining 10, plus RSS at readiness of the
# final run.
set -euo pipefail
cd "$(dirname "$0")/.."

FLAGS="${1:-}"
JAR=bench-app/build/libs/bench-app.jar
URL=http://localhost:8080/actuator/health/readiness
RUNS=11

[[ -f "$JAR" ]] || { echo "Build first: cd bench-app && ./gradlew bootJar"; exit 1; }

samples=()
rss_kb=""
aot_log=$(mktemp)
for i in $(seq 1 $RUNS); do
  # The discarded first run doubles as cache verification: it runs with
  # -Xlog:aot so we can fail loudly if the JVM silently ignored a requested
  # cache (version mismatch / stale classpath) - otherwise you benchmark nothing.
  extra_flags=""
  [[ $i -eq 1 && "$FLAGS" == *AOTCache=* ]] && extra_flags="-Xlog:aot"

  start_ns=$(date +%s%N)
  # shellcheck disable=SC2086
  java $FLAGS $extra_flags -jar "$JAR" >"$aot_log" 2>&1 &
  pid=$!

  until curl -sf -o /dev/null "$URL"; do
    sleep 0.005
    kill -0 $pid 2>/dev/null || { echo "app died during startup"; exit 1; }
  done
  ready_ms=$(( ($(date +%s%N) - start_ns) / 1000000 ))

  # RSS at readiness (Linux: /proc; macOS: ps)
  if [[ -r /proc/$pid/status ]]; then
    rss_kb=$(awk '/VmRSS/{print $2}' "/proc/$pid/status")
  else
    rss_kb=$(ps -o rss= -p $pid | tr -d ' ')
  fi

  kill $pid && wait $pid 2>/dev/null || true

  if [[ $i -eq 1 ]]; then
    if [[ "$FLAGS" == *AOTCache=* ]] && \
       grep -qi "Unable to use AOT cache\|AOT cache disabled" "$aot_log"; then
      echo "FATAL: AOT cache was requested but the JVM ignored it:"
      grep -i "aot" "$aot_log" | head -5
      exit 1
    fi
    echo "run $i: ${ready_ms}ms (discarded - cache priming + verification)"
  else
    echo "run $i: ${ready_ms}ms"
    samples+=("$ready_ms")
  fi
done

sorted=$(printf '%s\n' "${samples[@]}" | sort -n)
n=${#samples[@]}
median=$(echo "$sorted" | sed -n "$(( (n + 1) / 2 ))p")
echo "----"
echo "flags   : ${FLAGS:-<none>}"
echo "median  : ${median}ms   min: $(echo "$sorted" | head -1)ms   max: $(echo "$sorted" | tail -1)ms   (n=$n)"
echo "rss     : $(( rss_kb / 1024 ))MB at readiness (last run)"
