#!/usr/bin/env bash
# Shared helpers for every benchmark script. Source it, don't run it.
#
# A "config" is a colon-separated spec:   <jdk>:<gc>:<variant>[:<training>]
#   jdk      25 | 26 | 27 | 28 | native
#   gc       serial | g1 | z | shen          (ignored for native)
#   variant  none   - no AOT cache: pure interpreter + JIT baseline
#            cache  - AOT cache, but AOT CODE loading disabled at runtime (-XX:-AOTCodeCaching).
#                     On JDK 25-27 there is no AOT code to disable, so this is simply "the cache".
#            code   - JDK 28 only: AOT cache WITH JEP 544 compiled code.
#   training optional training-workload name (default: the app's representative workload)
#
# "cache" and "code" on JDK 28 use the SAME cache file - the only difference between them is
# whether the JVM is allowed to load the compiled code inside it. That isolates JEP 544 exactly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/work"
RESULTS="$ROOT/results/raw"
mkdir -p "$WORK/cache" "$RESULTS"

JDK_HOME_25="${JDK_HOME_25:-/opt/jdks/jdk25}"
JDK_HOME_26="${JDK_HOME_26:-/opt/jdks/jdk26}"
JDK_HOME_27="${JDK_HOME_27:-/opt/jdks/jdk27}"
JDK_HOME_28="${JDK_HOME_28:-/opt/jdks/jdk28}"
GRAALVM_HOME="${GRAALVM_HOME:-/opt/jdks/graal25}"

# Server under test on APP_CPUS, load generator on LOAD_CPUS - they never share a core.
APP_CPUS="${APP_CPUS:-0-1}"
LOAD_CPUS="${LOAD_CPUS:-2-3}"

SPRING_JAR="$WORK/spring-rest/spring-rest.jar"
WARMUP_JAR="$ROOT/apps/warmup-cpu/build/libs/warmup-cpu.jar"
CLI_JAR="$ROOT/apps/cli-tool/build/libs/flightlog.jar"
CLI_INPUT="$WORK/flights.csv"
LOADGEN="$ROOT/loadgen/loadgen"

# JAVA_TOOL_OPTIONS makes every JVM print "Picked up ..." and can change startup work.
# Benchmarks run with a clean environment.
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS

field() { echo "$1" | cut -d: -f"$2"; }

java_bin() { local v; v=$(field "$1" 1); eval echo "\$JDK_HOME_$v/bin/java"; }

gc_flag() {
  case "$(field "$1" 2)" in
    serial) echo "-XX:+UseSerialGC" ;;
    g1) echo "-XX:+UseG1GC" ;;
    z) echo "-XX:+UseZGC" ;;
    shen) echo "-XX:+UseShenandoahGC" ;;
    *) echo "unknown gc in $1" >&2; exit 1 ;;
  esac
}

# Default training workload per app.
default_training() {
  case "$1" in
    spring-rest) echo "full" ;;
    warmup-cpu) echo "adsb" ;;
    cli-tool) echo "summarize" ;;
  esac
}

# One cache file per (app, jdk, gc, training). AOT code is only usable with the GC it was
# trained with, and ZGC can't even map a compressed-oops cache - so GC is part of the key.
cache_path() {
  local app=$1 cfg=$2 training
  training=$(field "$cfg" 4); training=${training:-$(default_training "$app")}
  echo "$WORK/cache/$app-jdk$(field "$cfg" 1)-$(field "$cfg" 2)-$training.aot"
}

# JVM flags that select the cache for a production run.
cache_flags() {
  local app=$1 cfg=$2 jdk variant mode
  jdk=$(field "$cfg" 1); variant=$(field "$cfg" 3)
  # JDK 28 adds AOTMode=required (and deprecates "on"): refuse to start rather than
  # silently fall back to an uncached run. Either way we never benchmark a cache that
  # didn't load.
  if [[ $jdk == 28 ]]; then mode=required; else mode=on; fi
  case "$variant" in
    none) ;;
    cache)
      echo "-XX:AOTMode=$mode -XX:AOTCache=$(cache_path "$app" "$cfg")"
      [[ $jdk == 28 ]] && echo "-XX:+UnlockDiagnosticVMOptions -XX:-AOTCodeCaching"
      ;;
    code)
      [[ $jdk == 28 ]] || { echo "variant 'code' needs JDK 28 (JEP 544)" >&2; exit 1; }
      echo "-XX:AOTMode=$mode -XX:AOTCache=$(cache_path "$app" "$cfg")"
      ;;
    *) echo "unknown variant in $cfg" >&2; exit 1 ;;
  esac
}

# The app's command line after the JVM flags. $2 = workload argument (training name etc).
app_args() {
  local app=$1 workload=${2:-}
  case "$app" in
    spring-rest) echo "-jar $SPRING_JAR" ;;
    warmup-cpu) echo "-jar $WARMUP_JAR ${workload:-adsb} ${ITERATIONS:-30}" ;;
    cli-tool) echo "-jar $CLI_JAR summarize --input $CLI_INPUT --top 10" ;;
  esac
}

# Full production command for a config (word-split on purpose).
jvm_cmd() {
  local app=$1 cfg=$2 workload=${3:-}
  if [[ $(field "$cfg" 1) == native ]]; then
    case "$app" in
      spring-rest) echo "$WORK/native/spring-rest" ;;
      cli-tool) echo "$WORK/native/flightlog summarize --input $CLI_INPUT --top 10" ;;
      *) echo "no native build for $app" >&2; exit 1 ;;
    esac
    return
  fi
  echo "$(java_bin "$cfg") $(gc_flag "$cfg") $(cache_flags "$app" "$cfg" | tr '\n' ' ') $(app_args "$app" "$workload")"
}

# Wait until the Spring app answers, or die trying.
wait_ready() {
  local pid=$1
  until curl -sf -o /dev/null http://127.0.0.1:8080/actuator/health/readiness; do
    kill -0 "$pid" 2>/dev/null || { echo "app died during startup" >&2; return 1; }
    sleep 0.05
  done
}

stop_app() {
  local pid=$1
  kill -TERM "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

# Positive verification that a cached config really uses its cache (and, for "code",
# really loads AOT code). Benchmarking a cache that silently failed to map is the #1 way
# to publish nonsense, so every benchmark script calls this before measuring.
verify_config() {
  local app=$1 cfg=$2 log variant
  variant=$(field "$cfg" 3)
  [[ $variant == none || $(field "$cfg" 1) == native ]] && return 0
  log=$(mktemp)
  # shellcheck disable=SC2046
  case "$app" in
    spring-rest) taskset -c "$APP_CPUS" $(java_bin "$cfg") $(gc_flag "$cfg") $(cache_flags "$app" "$cfg") \
                   -Xlog:aot -XX:+PrintCompilation -Dbench.exitOnReady=true $(app_args "$app") >"$log" 2>&1 ;;
    *) taskset -c "$APP_CPUS" $(java_bin "$cfg") $(gc_flag "$cfg") $(cache_flags "$app" "$cfg") \
                   -Xlog:aot -XX:+PrintCompilation $(ITERATIONS=1 app_args "$app") >"$log" 2>&1 ;;
  esac
  if ! grep -q "Mapped static  *region" "$log" || grep -q "\[error *\]\[aot\]" "$log"; then
    echo "FATAL [$app $cfg]: AOT cache requested but not mapped:" >&2; grep -i aot "$log" | head -8 >&2; exit 1
  fi
  # "Loaded N AOT code entries" only means the code region was mapped and indexed - it is
  # logged even with -XX:-AOTCodeCaching. What counts is code actually INSTALLED for a
  # method, which -XX:+PrintCompilation marks with an "A" attribute ("AP" = preloaded).
  local installed
  installed=$(grep -cE '^ *[0-9]+ +[0-9]+ +[!%sbn]*A' "$log" || true)
  if [[ $variant == code && $installed -eq 0 ]]; then
    echo "FATAL [$app $cfg]: JEP 544 code requested but no AOT code was installed" >&2; exit 1
  fi
  if [[ $variant == cache && $installed -gt 0 ]]; then
    echo "FATAL [$app $cfg]: AOT code installed although disabled" >&2; exit 1
  fi
  echo "verified [$app $cfg]: cache mapped, $installed methods ran AOT code"
  rm -f "$log"
}

# Host + toolchain fingerprint, written next to the results.
fingerprint() {
  {
    echo "date: $(date -u +%FT%TZ)"
    echo "kernel: $(uname -sr)"
    echo "cpu: $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | xargs) x$(nproc)"
    echo "mem: $(free -g | awk '/Mem/{print $2}') GB"
    for v in 25 26 27 28; do echo "jdk$v: $(eval "\$JDK_HOME_$v/bin/java" -version 2>&1 | sed -n 2p)"; done
    echo "graalvm: $("$GRAALVM_HOME/bin/native-image" --version 2>&1 | head -1)"
    echo "hyperfine: $(hyperfine --version)"
  } >"$RESULTS/../environment.txt"
}
