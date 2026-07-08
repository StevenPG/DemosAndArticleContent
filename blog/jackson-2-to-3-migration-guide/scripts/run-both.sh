#!/usr/bin/env bash
# ===========================================================================
# run-both.sh - builds and starts both example apps side by side.
#
#   jackson2-example  ->  http://localhost:8082  (Spring Boot 3.5, Jackson 2)
#   jackson3-example   ->  http://localhost:8083  (Spring Boot 4.1, Jackson 3)
#
# Then hit compare-requests.sh to see identical requests handled by both.
# ===========================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/.demo"
mkdir -p "$DEMO_DIR"

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

for project in jackson2-example jackson3-example; do
    echo "==> Building $project"
    (cd "$ROOT_DIR/$project" && ./gradlew --console=plain -q build -x test)
done

echo "==> Starting jackson2-example (Spring Boot 3.5 / Jackson 2, :8082)"
(cd "$ROOT_DIR/jackson2-example" && nohup java -jar build/libs/jackson2-example-0.0.1-SNAPSHOT.jar \
    > "$DEMO_DIR/jackson2-example.log" 2>&1 & echo $! > "$DEMO_DIR/jackson2-example.pid")

echo "==> Starting jackson3-example (Spring Boot 4.1 / Jackson 3, :8083)"
(cd "$ROOT_DIR/jackson3-example" && nohup java -jar build/libs/jackson3-example-0.0.1-SNAPSHOT.jar \
    > "$DEMO_DIR/jackson3-example.log" 2>&1 & echo $! > "$DEMO_DIR/jackson3-example.pid")

wait_for_http "http://localhost:8082/api/posts/sample" "jackson2-example" 90
wait_for_http "http://localhost:8083/api/posts/sample" "jackson3-example" 90

cat <<EOF

Both apps are running:

  jackson2-example   http://localhost:8082   (log: .demo/jackson2-example.log)
  jackson3-example   http://localhost:8083   (log: .demo/jackson3-example.log)

Next: ./scripts/compare-requests.sh
      ./scripts/stop-both.sh
EOF
