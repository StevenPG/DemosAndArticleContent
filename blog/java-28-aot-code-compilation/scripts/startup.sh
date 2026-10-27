#!/usr/bin/env bash
# Startup benchmark with hyperfine.
#   spring-rest: exec -> ApplicationReadyEvent (the app halts itself, -Dbench.exitOnReady=true)
#   cli-tool:    exec -> process exit (the whole job)
#
# Usage: TAG=ladder ./scripts/startup.sh <spring-rest|cli-tool> <config>...
#   e.g. ./scripts/startup.sh spring-rest 28:g1:none 28:g1:cache 28:g1:code native
source "$(dirname "$0")/lib.sh"
app=$1; shift
TAG="${TAG:-startup}"
RUNS="${RUNS:-20}"
WARMUP="${WARMUP:-3}"

args=()
for cfg in "$@"; do
  verify_config "$app" "$cfg"
  cmd=$(jvm_cmd "$app" "$cfg")
  if [[ $app == spring-rest ]]; then
    if [[ $(field "$cfg" 1) == native ]]; then cmd="$cmd -Dbench.exitOnReady=true"
    else cmd="${cmd/ -jar / -Dbench.exitOnReady=true -jar }"; fi
  fi
  args+=(-n "$cfg" "taskset -c $APP_CPUS $cmd")
done

# -N: no intermediate shell. Output is discarded by hyperfine. Warmup runs prime the page
# cache (the .aot file and jars) so we measure the JVM, not the disk.
hyperfine -N --style basic --warmup "$WARMUP" --runs "$RUNS" \
  --export-json "$RESULTS/startup-$TAG-$app.json" "${args[@]}"
