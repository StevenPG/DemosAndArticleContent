#!/usr/bin/env bash
# Build jarbench:builder - the stock Temurin JDK image plus an already-populated
# Gradle cache - and point the Dockerfiles at it with:
#
#   docker build --build-arg BUILD_IMAGE=jarbench:builder \
#                --build-arg GRADLE_ARGS=--offline ...
#
# Two reasons to want this:
#   1. Air-gapped builds. Resolve dependencies once on a connected machine, bake
#      them into a builder image, then build anywhere with --offline.
#   2. Benchmarking. Dependency downloads are network-bound and would drown the
#      differences between packaging variants in jitter.
#
# Usage: ./seed-builder-image.sh [GRADLE_USER_HOME]   (default: ~/.gradle)
set -euo pipefail

GRADLE_HOME="${1:-$HOME/.gradle}"
if [ ! -d "$GRADLE_HOME/caches" ]; then
  echo "no Gradle cache at $GRADLE_HOME - run ./gradlew bootJar in bench-app first" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "packing $GRADLE_HOME ..."
tar -cf "$WORK/gradle-home.tar" -C "$GRADLE_HOME" .

cat > "$WORK/Dockerfile" <<'DOCKERFILE'
FROM eclipse-temurin:25-jdk-noble
COPY gradle-home.tar /tmp/gradle-home.tar
RUN mkdir -p /root/.gradle \
 && tar -xf /tmp/gradle-home.tar -C /root/.gradle \
 && rm /tmp/gradle-home.tar
DOCKERFILE

docker build --progress=plain -t jarbench:builder "$WORK"
echo "built jarbench:builder"
