#!/usr/bin/env bash
# Show where every machine is in its run: RUNNING / DONE / FAILED, plus the last log lines.
#
# Usage: scripts/status.sh [-v]     (-v: last 5 lines of each bootstrap log)
set -euo pipefail
cd "$(dirname "$0")/../terraform"

bucket="$(terraform output -raw results_bucket)"
region="$(terraform output -raw region)"
machines="$(terraform output -json machines | python3 -c 'import json,sys; print(" ".join(json.load(sys.stdin)))')"

for m in ${machines}; do
  state="$(aws s3 cp --region "${region}" "s3://${bucket}/results/${m}/STATUS" - 2>/dev/null || echo BOOTING)"
  printf '%-16s %s\n' "${m}" "${state}"
  if [[ "${1:-}" == "-v" ]]; then
    aws s3 cp --region "${region}" "s3://${bucket}/results/${m}/bootstrap.log" - 2>/dev/null | tail -5 | sed 's/^/    /' || true
  fi
done
