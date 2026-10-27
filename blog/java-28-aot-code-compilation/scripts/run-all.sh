#!/usr/bin/env bash
# The full experiment suite behind the article. Takes ~3-4 hours on a 4-core box.
# Each phase can also be run on its own: ./scripts/run-all.sh ladder curves ...
#
# Prerequisites: ./gradlew build, ./scripts/prepare.sh, ./scripts/native-build.sh
source "$(dirname "$0")/lib.sh"
phases=("$@")
[[ ${#phases[@]} -eq 0 ]] && phases=(train ladder batch curves mismatch gcmatrix fixedheap)
here="$(dirname "$0")"
fingerprint

for phase in "${phases[@]}"; do
  echo "################ phase: $phase  ($(date -u +%T))"
  case "$phase" in

    train)  # default G1 caches for every JDK and app
      for v in 25 26 27 28; do
        for app in spring-rest warmup-cpu cli-tool; do "$here/train.sh" "$app" "$v:g1"; done
      done
      "$here/train.sh" warmup-cpu 28:g1:code:javac
      ;;

    ladder)  # startup: the JDK version ladder + the JDK 28 ablation + native image
      cfgs=(28:g1:none 25:g1:cache 26:g1:cache 27:g1:cache 28:g1:cache 28:g1:code native)
      TAG=ladder "$here/startup.sh" spring-rest "${cfgs[@]}"
      TAG=ladder "$here/startup.sh" cli-tool "${cfgs[@]}"
      ;;

    batch)  # in-process warmup curves (iteration times)
      TAG=ladder MODE=adsb "$here/batch.sh" 28:g1:none 25:g1:cache 26:g1:cache 27:g1:cache 28:g1:cache 28:g1:code
      TAG=jep MODE=javac ITERATIONS=20 "$here/batch.sh" 28:g1:none 28:g1:cache:javac 28:g1:code:javac
      ;;

    curves)  # spring-rest throughput/latency over the first 120 s
      TAG=main "$here/curve.sh" 28:g1:none 25:g1:cache 28:g1:cache 28:g1:code native
      ;;

    mismatch)  # train on the wrong thing, run the real thing
      "$here/train.sh" spring-rest 28:g1:code:fleet
      TAG=mismatch REPS=3 "$here/curve.sh" 28:g1:none 28:g1:code:fleet 28:g1:code
      # warmup-cpu: adsb-trained cache running javac, and vice versa
      TAG=mismatch MODE=javac ITERATIONS=20 "$here/batch.sh" 28:g1:none 28:g1:code:adsb 28:g1:code:javac
      TAG=mismatch MODE=adsb "$here/batch.sh" 28:g1:none 28:g1:code:javac 28:g1:code:adsb
      ;;

    gcmatrix)  # every collector x 1 and 2 CPUs
      for gc in serial g1 z shen; do
        [[ $gc == g1 ]] || { "$here/train.sh" spring-rest "28:$gc"; "$here/train.sh" warmup-cpu "28:$gc"; }
      done
      for cpus in 0 0-1; do
        n=$([[ $cpus == 0 ]] && echo 1 || echo 2)
        for gc in serial g1 z shen; do
          c=(28:$gc:none 28:$gc:cache 28:$gc:code)
          APP_CPUS=$cpus TAG=gc-${n}cpu-$gc RUNS=10 "$here/startup.sh" spring-rest "${c[@]}"
          APP_CPUS=$cpus TAG=gc-${n}cpu-$gc REPS=3 "$here/batch.sh" "${c[@]}"
          APP_CPUS=$cpus TAG=gc-${n}cpu REPS=2 DURATION=60 "$here/curve.sh" "${c[@]}"
        done
      done
      ;;

    fixedheap)  # the adsb batch again with a fixed 1 GB heap and more repetitions, to cut GC noise.
      # Caches are trained with the same heap flags (own cache dir) - see TRAIN_JVM in lib.sh.
      export CACHE_DIR="$WORK/cache-fixedheap" TRAIN_JVM="-Xms1g -Xmx1g" EXTRA_JVM="-Xms1g -Xmx1g"
      for v in 25 26 27 28; do "$here/train.sh" warmup-cpu "$v:g1"; done
      TAG=fixedheap MODE=adsb REPS=10 \
        "$here/batch.sh" 28:g1:none 25:g1:cache 26:g1:cache 27:g1:cache 28:g1:cache 28:g1:code
      unset CACHE_DIR TRAIN_JVM EXTRA_JVM
      ;;

    *) echo "unknown phase $phase"; exit 1 ;;
  esac
done
echo "################ done ($(date -u +%T))"
