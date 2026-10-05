#!/usr/bin/env bash
# Run the same workloads and eval set against Cloudflare's hosted @cf/cloudflare/clef-flash.
#
# Needs CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN (a token with the "Workers AI: Read"
# permission is enough). Each step runs for 20 s, so at Workers AI latencies expect several hundred
# requests and 1-2M input tokens: roughly $0.10-0.20 at $0.09/1M, partly covered by the free daily
# neuron allocation.
#
# Latency here includes the network round trip from wherever you run this. To compare like with
# like, also run it from one of the EC2 boxes (scripts/remote is already there):
#   aws ssm start-session --target <id>
#   sudo -i
#   export CLOUDFLARE_ACCOUNT_ID=... CLOUDFLARE_API_TOKEN=...
#   /opt/clef-bench/scripts/bench-workers-ai.sh workers-ai-from-us-east-1
#   (results are uploaded to the run's S3 bucket automatically when /etc/clef-bench.env exists)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON="${PYTHON:-python3}"
[[ -x "${ROOT}/.venv/bin/python" ]] && PYTHON="${ROOT}/.venv/bin/python"

label="${1:-workers-ai}"
"${PYTHON}" "${ROOT}/bench/clef_bench.py" \
  --target workers-ai \
  --label "${label}" \
  --instance-type workers-ai \
  --quant hosted \
  --concurrency 1,4 \
  --duration 20 \
  --min-requests 10 \
  --eval \
  --extra "{\"instance_name\": \"${label}\", \"client_host\": \"$(hostname)\"}" \
  --out "${ROOT}/results/${label}/workers-ai.json"

if [[ -f /etc/clef-bench.env ]]; then
  # shellcheck disable=SC1091
  source /etc/clef-bench.env
  aws s3 sync --only-show-errors --region "${AWS_REGION}" "${ROOT}/results/${label}" "s3://${BUCKET}/results/${label}"
  echo "uploaded to s3://${BUCKET}/results/${label}"
fi
