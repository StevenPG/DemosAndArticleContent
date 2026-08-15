#!/usr/bin/env bash
# Stops the two demo apps started by run-both.sh, using their PID files.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/.demo"

for name in jackson2-example jackson3-example; do
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
