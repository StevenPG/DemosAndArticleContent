#!/usr/bin/env bash
# Stops the two demo services started by run-demo.sh, using their PID files.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/.demo"

for name in inventory-server storefront-client; do
    pid_file="$DEMO_DIR/$name.pid"
    if [[ -f "$pid_file" ]]; then
        pid=$(cat "$pid_file")
        if kill "$pid" 2>/dev/null; then
            echo "Stopped $name (pid $pid)"
        else
            echo "$name (pid $pid) was not running"
        fi
        rm -f "$pid_file"
    else
        echo "No pid file for $name - nothing to stop"
    fi
done
