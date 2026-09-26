#!/usr/bin/env bash
# JEP 536: JFR redacts secrets by default on JDK 27. Records a short flight
# recording with a secret in an environment variable, a system property and a
# JVM argument, then prints what ended up in the .jfr file.
#
#   ./probes/jfr-redaction.sh .jdks/jdk26 .jdks/jdk27
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf 'public class Idle { public static void main(String[] a) throws Exception { Thread.sleep(1500); } }\n' > "$tmp/Idle.java"

for jdk in "$@"; do
  echo "== $("$jdk/bin/java" -version 2>&1 | grep -v "Picked up" | head -1)"
  DB_PASSWORD=hunter2 API_TOKEN=abc123 AWS_REGION=us-east-1 \
    "$jdk/bin/java" -XX:StartFlightRecording:filename="$tmp/r.jfr",settings=default \
      -Dapp.secret=s3cr3t "$tmp/Idle.java" >/dev/null 2>&1
  "$jdk/bin/jfr" print --events jdk.InitialEnvironmentVariable,jdk.InitialSystemProperty "$tmp/r.jfr" \
    | grep -A1 -E 'key = "(DB_PASSWORD|API_TOKEN|AWS_REGION|app.secret)"' | grep -E 'key|value' | paste - -
  "$jdk/bin/jfr" print --events jdk.JVMInformation "$tmp/r.jfr" | grep jvmArguments
  rm -f "$tmp/r.jfr"
done
