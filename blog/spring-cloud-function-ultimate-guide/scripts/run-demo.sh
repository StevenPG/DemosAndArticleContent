#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Builds everything and starts every surface the same functions are exposed on:
#
#   Kafka (docker)      :9092   broker for the streaming surface
#   app-web             :8080   functions as HTTP endpoints
#   app-rsocket         :7000   functions as RSocket routes (TCP)
#   app-stream-kafka     ----   functions bound to Kafka topics (no HTTP port)
#
# Then run ./scripts/demo-requests.sh to exercise the features, and
# ./scripts/stop-demo.sh to tear it all down.
# -----------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

DEMO_DIR=".demo"
mkdir -p "$DEMO_DIR"
: > "$DEMO_DIR/pids"

# Gradle 8.14 (this project's wrapper) cannot itself run on JDK 24+ as its host
# JVM. This is separate from the project's Java 21 toolchain (which compiles/runs
# the apps) — if `java` on PATH is 24+, hunt for an installed 17-23 JDK to drive
# Gradle.
find_compatible_java_home() {
  local major
  major=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major <= 23 )); then
    return 0
  fi
  local candidate
  for candidate in "$HOME"/.sdkman/candidates/java/21.*-tem "$HOME"/.sdkman/candidates/java/17.*-tem; do
    if [[ -x "$candidate/bin/java" ]]; then printf '%s' "$candidate"; return 0; fi
  done
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    mac_home=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)
    if [[ -n "$mac_home" ]]; then printf '%s' "$mac_home"; return 0; fi
  fi
  return 1
}

echo "==> Checking for a Gradle-compatible JDK (8.14 can't run on JDK 24+)"
if java_home_override=$(find_compatible_java_home); then
  if [[ -n "$java_home_override" ]]; then
    export JAVA_HOME="$java_home_override"
    echo "    default java is too new for Gradle 8.14 — using $JAVA_HOME instead"
  fi
else
  echo "!! No JDK 17-23 found (only JDK 24+). Install one, e.g. sdk install java 21.0.3-tem"
  exit 1
fi

echo "==> Starting Kafka (docker compose)"
docker compose up -d kafka

echo "==> Building boot jars"
./gradlew bootJar --console=plain -q

start() { # name  jar  extra-args...
  local name="$1"; local jar="$2"; shift 2
  echo "==> Starting $name"
  nohup java -jar "$jar" "$@" > "$DEMO_DIR/$name.log" 2>&1 &
  echo "$!" >> "$DEMO_DIR/pids"
}

start app-web          app-web/build/libs/app-web-0.0.1-SNAPSHOT.jar
start app-rsocket      app-rsocket/build/libs/app-rsocket-0.0.1-SNAPSHOT.jar
start app-stream-kafka app-stream-kafka/build/libs/app-stream-kafka-0.0.1-SNAPSHOT.jar

wait_health() { # url  name
  local url="$1"; local name="$2"
  for _ in $(seq 1 60); do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then echo "    $name is up"; return 0; fi
    sleep 1
  done
  echo "    !! $name did not become healthy — see $DEMO_DIR/$name.log"; return 1
}

wait_log() { # pattern  name
  local pattern="$1"; local name="$2"
  for _ in $(seq 1 60); do
    if grep -q "$pattern" "$DEMO_DIR/$name.log" 2>/dev/null; then echo "    $name is up"; return 0; fi
    sleep 1
  done
  echo "    !! $name did not start — see $DEMO_DIR/$name.log"; return 1
}

echo "==> Waiting for services"
wait_health http://localhost:8080/actuator/health app-web
wait_log "Started RSocketApplication" app-rsocket
wait_log "Started StreamApplication"  app-stream-kafka

echo
echo "All up. HTTP on :8080, RSocket on :7000, Kafka on :9092."
echo "Next:  ./scripts/demo-requests.sh"
