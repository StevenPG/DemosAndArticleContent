#!/usr/bin/env bash
# Starts the identical baseline app on Spring Boot 4.1 and 4.2 and sends the same
# requests to each, showing the behavior changes covered in the post:
#   - stricter MIME parsing (malformed Accept -> 406 on 4.2)
#   - relative redirects by default (Location header)
#
# Usage (from the project root): ./scripts/behavior-diff.sh
set -euo pipefail

cd "$(dirname "$0")/.."
MVN="${MVN:-./mvnw}"
PORT=18081
LOG="$(mktemp)"

for v in boot-4.1 boot-4.2; do
  "$MVN" -q -DskipTests -f "startup-baseline/$v/pom.xml" package
  java -jar "startup-baseline/$v/target/boot42-0.0.1-SNAPSHOT.jar" --server.port=$PORT >"$LOG" 2>&1 &
  pid=$!
  for _ in $(seq 1 40); do
    curl -s "localhost:$PORT/hello" >/dev/null && break
    sleep 0.5
  done

  echo "===== $v"
  echo -n "duplicate Accept params: "
  curl -s -o /dev/null -w "%{http_code}\n" -H 'Accept: text/plain;charset=UTF-8;CHARSET=ISO-8859-1' "localhost:$PORT/hello"
  echo -n "valueless Accept param:  "
  curl -s -o /dev/null -w "%{http_code}\n" -H 'Accept: text/plain;foo' "localhost:$PORT/hello"
  echo -n "redirect:                "
  curl -s -i "localhost:$PORT/old" | grep -i "^location"

  kill "$pid"
  wait "$pid" 2>/dev/null || true
done
