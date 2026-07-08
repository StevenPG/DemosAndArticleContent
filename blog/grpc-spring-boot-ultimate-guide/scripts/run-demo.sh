#!/usr/bin/env bash
# ===========================================================================
# run-demo.sh - build everything, start both services, wait until healthy.
#
#   ./scripts/run-demo.sh          # start server + client in the background
#   ./scripts/demo-requests.sh     # then exercise every RPC type
#   ./scripts/stop-demo.sh         # tear everything down
#
# Logs land in .demo/inventory-server.log and .demo/storefront-client.log.
# ===========================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/.demo"
mkdir -p "$DEMO_DIR"

GRPC_PORT=9090
HTTP_PORT=8080

wait_for_tcp() { # host port name timeout_seconds
    local host=$1 port=$2 name=$3 timeout=${4:-60}
    echo -n "Waiting for $name on $host:$port "
    for ((i = 0; i < timeout; i++)); do
        # /dev/tcp is a bash built-in: opens a TCP connection, no netcat needed.
        if (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null; then
            exec 3>&- 3<&- || true
            echo " up!"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo " TIMED OUT"
    return 1
}

wait_for_http() { # url name timeout_seconds
    local url=$1 name=$2 timeout=${3:-60}
    echo -n "Waiting for $name at $url "
    for ((i = 0; i < timeout; i++)); do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo " up!"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo " TIMED OUT"
    return 1
}

echo "==> Building all modules (this also regenerates gRPC code from the .proto)"
(cd "$ROOT_DIR" && ./gradlew --console=plain -q build -x test)

echo "==> Starting inventory-server (gRPC on :$GRPC_PORT)"
(cd "$ROOT_DIR" && nohup java -jar inventory-server/build/libs/inventory-server-0.0.1-SNAPSHOT.jar \
    > "$DEMO_DIR/inventory-server.log" 2>&1 & echo $! > "$DEMO_DIR/inventory-server.pid")

echo "==> Starting storefront-client (REST on :$HTTP_PORT -> gRPC client)"
(cd "$ROOT_DIR" && nohup java -jar storefront-client/build/libs/storefront-client-0.0.1-SNAPSHOT.jar \
    > "$DEMO_DIR/storefront-client.log" 2>&1 & echo $! > "$DEMO_DIR/storefront-client.pid")

wait_for_tcp localhost "$GRPC_PORT" "inventory-server (gRPC)" 90
wait_for_http "http://localhost:$HTTP_PORT/actuator/health" "storefront-client (REST)" 90

cat <<EOF

Both services are running:

  inventory-server   gRPC  localhost:$GRPC_PORT   (log: .demo/inventory-server.log)
  storefront-client  HTTP  localhost:$HTTP_PORT   (log: .demo/storefront-client.log)

Next steps:
  ./scripts/demo-requests.sh   # exercise all four RPC types via the REST edge
  ./scripts/grpcurl-examples.sh# talk gRPC directly (requires grpcurl)
  ./scripts/stop-demo.sh       # shut everything down
EOF
