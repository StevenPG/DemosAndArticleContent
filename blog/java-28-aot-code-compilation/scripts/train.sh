#!/usr/bin/env bash
# Training run: one JVM invocation with -XX:AOTCacheOutput (JEP 514 one-step workflow).
# The JVM records what the workload loads, links, profiles and - on JDK 28 - compiles, and
# writes the cache when it exits.
#
# Usage: ./scripts/train.sh <spring-rest|warmup-cpu|cli-tool> <jdk>:<gc>[:<variant>[:<training>]]
#   training workloads: spring-rest -> full | fleet | tracks   (loadgen request mix)
#                       warmup-cpu  -> adsb | javac
#                       cli-tool    -> summarize
#   e.g. ./scripts/train.sh spring-rest 28:g1
#        ./scripts/train.sh spring-rest 28:g1:code:fleet   # deliberately narrow training
source "$(dirname "$0")/lib.sh"

app=$1 cfg=$2
training=$(field "$cfg" 4); training=${training:-$(default_training "$app")}
cache=$(cache_path "$app" "$cfg")
java=$(java_bin "$cfg")
TRAIN_SECONDS="${TRAIN_SECONDS:-30}"
rm -f "$cache"

echo "==> training $app on JDK $(field "$cfg" 1) / $(field "$cfg" 2) with workload '$training' -> $(basename "$cache")"
start=$(date +%s%N)
case "$app" in
  spring-rest)
    # shellcheck disable=SC2046
    taskset -c "$APP_CPUS" "$java" $(gc_flag "$cfg") -XX:AOTCacheOutput="$cache" $(app_args "$app") \
      >"$WORK/train-$app.log" 2>&1 &
    pid=$!
    wait_ready "$pid"
    taskset -c "$LOAD_CPUS" "$LOADGEN" -mix "$training" -duration "${TRAIN_SECONDS}s" -c 8 >/dev/null
    stop_app "$pid"   # SIGTERM -> orderly JVM exit -> cache is written
    ;;
  warmup-cpu)
    # shellcheck disable=SC2046
    ITERATIONS=10 taskset -c "$APP_CPUS" "$java" $(gc_flag "$cfg") -XX:AOTCacheOutput="$cache" \
      $(ITERATIONS=10 app_args "$app" "$training") >"$WORK/train-$app.log" 2>&1
    ;;
  cli-tool)
    # shellcheck disable=SC2046
    taskset -c "$APP_CPUS" "$java" $(gc_flag "$cfg") -XX:AOTCacheOutput="$cache" $(app_args "$app") \
      >"$WORK/train-$app.log" 2>&1
    ;;
esac
elapsed=$(( ($(date +%s%N) - start) / 1000000 ))
[[ -s "$cache" ]] || { echo "FATAL: no cache written. Log:"; tail -20 "$WORK/train-$app.log"; exit 1; }
size=$(stat -c %s "$cache")
echo "$app,$cfg,$training,$elapsed,$size" >>"$RESULTS/training.csv"
echo "    wrote $(( size / 1024 / 1024 )) MB in ${elapsed} ms"
