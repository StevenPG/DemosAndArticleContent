#!/usr/bin/env bash
# Training runs: JEP 514-style, one invocation per cache.
# The "training" profile makes the app exercise its own hot paths over real
# HTTP and then exit cleanly, at which point the JVM writes the cache.
#
# TWO caches, not one. JEP 516 made the *object* cache GC-agnostic, but the
# cache still records the oop encoding it was built with, and ZGC does not
# support compressed oops. Feeding a G1-built cache (UseCompressedOops = 1)
# to ZGC gets you:
#
#   [info   ][aot] The AOT cache was created with UseCompressedOops = 1, ...
#   [warning][aot] Unable to use AOT cache.
#   [error  ][aot] Unable to map shared spaces
#
# and the JVM continues, uncached, at full cold-start cost. So: app.aot for the
# compressed-oops collectors (G1, Serial, Parallel) and app-zgc.aot for ZGC.
# "One cache artifact per app version" is only true within one oop encoding.
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=bench-app/build/libs/bench-app.jar
[[ -f "$JAR" ]] || { echo "Build first: cd bench-app && ./gradlew bootJar"; exit 1; }

echo "==> training app.aot (default GC - compressed oops)"
java -XX:AOTCacheOutput=app.aot \
     -Dspring.profiles.active=training \
     -jar "$JAR"

echo "==> training app-zgc.aot (ZGC - no compressed oops)"
java -XX:AOTCacheOutput=app-zgc.aot \
     -XX:+UseZGC \
     -Dspring.profiles.active=training \
     -jar "$JAR"

ls -lh app.aot app-zgc.aot
