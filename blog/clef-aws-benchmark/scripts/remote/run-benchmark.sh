#!/usr/bin/env bash
# Benchmark every configured quantization on this machine, one server at a time.
#
# For each quant: start llama-server, time launch -> /health 200 (model load, incl. upload to the
# GPU), run bench/clef_bench.py with the server PID for CPU/RSS sampling, stop the server.
#
# Env (from /etc/clef-bench.env on EC2, or set by hand):
#   QUANTS          "Q4_K_M Q8_0 BF16"
#   INSTANCE_NAME   label used for the results folder (default: hostname)
#   INSTANCE_TYPE   EC2 instance type (default: from IMDSv2, or "local")
#   BENCH_ARGS      extra clef_bench.py args, e.g. "--concurrency 1,4 --duration 45"
#   RUN_EVAL        "true" (default) also answers the labeled cases in bench/data/eval_cases.json
#   RESULTS_DIR     default /opt/clef-bench/results/$INSTANCE_NAME
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"
PYTHON="${PYTHON:-${ROOT}/.venv/bin/python}"
[[ -x "${PYTHON}" ]] || PYTHON=python3

imds_instance_type() {
  local token
  token="$(curl -sf -m 2 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" || return 1
  curl -sf -m 2 -H "X-aws-ec2-metadata-token: ${token}" http://169.254.169.254/latest/meta-data/instance-type
}

QUANTS="${QUANTS:-Q4_K_M Q8_0 BF16}"
INSTANCE_NAME="${INSTANCE_NAME:-$(hostname -s)}"
INSTANCE_TYPE="${INSTANCE_TYPE:-$(imds_instance_type || echo local)}"
RESULTS_DIR="${RESULTS_DIR:-${ROOT}/results/${INSTANCE_NAME}}"
PORT="${CLEF_PORT:-8080}"
mkdir -p "${RESULTS_DIR}"
eval_flag=()
[[ "${RUN_EVAL:-true}" == "true" ]] && eval_flag=(--eval)

LLAMA_SERVER="${LLAMA_SERVER:-/opt/llama.cpp/bin/llama-server}"
llama_tag="$(cat "$(dirname "${LLAMA_SERVER}")/../TAG" 2>/dev/null || echo unknown)"

for quant in ${QUANTS}; do
  echo "=== ${INSTANCE_NAME} (${INSTANCE_TYPE}) ${quant}"
  # drop the page cache so every quant's load time is a cold read from EBS, not RAM
  sync && echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true

  start=$(date +%s.%N)
  "${HERE}/serve.sh" "${quant}" > "${RESULTS_DIR}/${quant}.server.log" 2>&1 &
  pid=$!

  ready=""
  for _ in $(seq 1 1800); do
    if curl -sf "http://127.0.0.1:${PORT}/health" >/dev/null; then ready=yes; break; fi
    kill -0 "${pid}" 2>/dev/null || break
    sleep 0.5
  done
  if [[ -z "${ready}" ]]; then
    echo "!!! ${quant}: server did not become healthy, see ${quant}.server.log"
    tail -20 "${RESULTS_DIR}/${quant}.server.log"
    kill "${pid}" 2>/dev/null; wait "${pid}" 2>/dev/null
    continue
  fi
  startup=$(awk -v s="${start}" -v e="$(date +%s.%N)" 'BEGIN { printf "%.2f", e - s }')
  echo "    ready in ${startup}s"

  # shellcheck disable=SC2086
  "${PYTHON}" "${ROOT}/bench/clef_bench.py" \
    --label "${INSTANCE_NAME}/${quant}" \
    --instance-type "${INSTANCE_TYPE}" \
    --quant "${quant}" \
    --server-pid "${pid}" \
    --startup-seconds "${startup}" \
    "${eval_flag[@]}" \
    --extra "{\"instance_name\": \"${INSTANCE_NAME}\", \"llama_cpp_tag\": \"${llama_tag}\"}" \
    --out "${RESULTS_DIR}/${quant}.json" \
    ${BENCH_ARGS:-} || echo "!!! ${quant}: benchmark failed"

  kill "${pid}" 2>/dev/null
  wait "${pid}" 2>/dev/null
done

echo "results in ${RESULTS_DIR}"
