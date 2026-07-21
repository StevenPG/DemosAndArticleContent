#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Builds everything and starts the whole system:
#
#   Redis (docker)      :6379   rate-limiter storage for both gateways
#   backend-orders      :8081   downstream service
#   backend-inventory   :8082   downstream service, instance A
#   backend-inventory   :8083   downstream service, instance B
#   gateway-webflux     :8080   reactive gateway (dev profile -> /dev/token enabled)
#   gateway-webmvc      :8090   servlet gateway  (dev profile -> /dev/token enabled)
#
# Then run ./scripts/demo-requests.sh to exercise every feature, and
# ./scripts/stop-demo.sh to tear it all down.
# -----------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

DEMO_DIR=".demo"
mkdir -p "$DEMO_DIR"
: > "$DEMO_DIR/pids"

# Gradle 8.14 (this project's wrapper) cannot itself run on JDK 24+ — its bundled
# Kotlin DSL compiler fails to parse the host JVM's own version string ("25" is not
# a value it understands). This is separate from the project's Java 21 toolchain
# (which is what actually compiles/runs the app) — if `java` on PATH is 24+, hunt
# for an installed 17-23 JDK and use that just to drive Gradle.
find_compatible_java_home() {
  local major
  major=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major <= 23 )); then
    return 0   # current default java already works
  fi

  local candidate
  for candidate in "$HOME"/.sdkman/candidates/java/21.*-tem "$HOME"/.sdkman/candidates/java/17.*-tem; do
    if [[ -x "$candidate/bin/java" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    mac_home=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)
    if [[ -n "$mac_home" ]]; then
      printf '%s' "$mac_home"
      return 0
    fi
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
  echo "!! No JDK 17-23 found on this machine (only JDK 24+), and Gradle 8.14 cannot run on those."
  echo "   Install one, e.g. via SDKMAN: sdk install java 21.0.3-tem"
  echo "   ...then either 'sdk use java 21.0.3-tem' or export JAVA_HOME to it and re-run this script."
  exit 1
fi

echo "==> Starting Redis (docker compose)"
docker compose up -d redis

echo "==> Building boot jars"
./gradlew bootJar --console=plain -q

start() { # name  jar  extra-args...
  local name="$1"; local jar="$2"; shift 2
  echo "==> Starting $name"
  nohup java -jar "$jar" "$@" > "$DEMO_DIR/$name.log" 2>&1 &
  echo "$!" >> "$DEMO_DIR/pids"
}

start backend-orders      backend-orders/build/libs/backend-orders-0.0.1-SNAPSHOT.jar
start backend-inventory-A backend-inventory/build/libs/backend-inventory-0.0.1-SNAPSHOT.jar --server.port=8082
start backend-inventory-B backend-inventory/build/libs/backend-inventory-0.0.1-SNAPSHOT.jar --server.port=8083
start gateway-webflux     gateway-webflux/build/libs/gateway-webflux-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
start gateway-webmvc      gateway-webmvc/build/libs/gateway-webmvc-0.0.1-SNAPSHOT.jar   --spring.profiles.active=dev

wait_health() { # url  name
  local url="$1"; local name="$2"
  for _ in $(seq 1 60); do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then echo "    $name is up"; return 0; fi
    sleep 1
  done
  echo "    !! $name did not become healthy — see $DEMO_DIR/$name.log"; return 1
}

echo "==> Waiting for services"
wait_health http://localhost:8081/actuator/health backend-orders
wait_health http://localhost:8082/actuator/health backend-inventory-A
wait_health http://localhost:8083/actuator/health backend-inventory-B
wait_health http://localhost:8080/actuator/health gateway-webflux
wait_health http://localhost:8090/actuator/health gateway-webmvc

echo
echo "All up. Reactive gateway on :8080, servlet gateway on :8090."
echo "Next:  ./scripts/demo-requests.sh"
