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
