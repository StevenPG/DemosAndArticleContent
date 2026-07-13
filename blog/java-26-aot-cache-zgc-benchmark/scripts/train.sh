#!/usr/bin/env bash
# Training run: one JEP 514-style invocation creates the AOT cache.
# The "training" profile makes the app exercise its own hot paths over real
# HTTP and then exit cleanly, at which point the JVM writes app.aot.
#
# Note: default GC on purpose. Since JEP 516 (JDK 26) the cache format is
# GC-agnostic - train with G1 here, consume it under ZGC/Serial in the matrix.
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=bench-app/build/libs/bench-app.jar
[[ -f "$JAR" ]] || { echo "Build first: cd bench-app && ./gradlew bootJar"; exit 1; }

java -XX:AOTCacheOutput=app.aot \
     -Dspring.profiles.active=training \
     -jar "$JAR"

ls -lh app.aot
