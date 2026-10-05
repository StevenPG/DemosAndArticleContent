#!/usr/bin/env bash
# Run llama-server for one Clef-Flash quantization, in the foreground (exec, so $! is the server PID).
#
# Clef evaluates the whole prompt in a single micro-batch: anything longer than --ubatch-size is
# rejected. CLEF_CTX sets context, batch and ubatch together. 8192 covers every workload here
# (largest ~4.5k tokens). Clef-flash accepts up to 64k, so raise it for long states (it costs memory).
#
# A server that loads a decision model serves /v1/systemone only; there is no text generation, and
# llama.cpp switches the context into embedding mode by itself.
#
# Usage: ./serve.sh Q4_K_M [extra llama-server args...]
set -euo pipefail

QUANT="${1:-${CLEF_QUANT:-Q4_K_M}}"
shift || true
MODEL_DIR="${CLEF_MODEL_DIR:-/opt/models}"
CTX="${CLEF_CTX:-8192}"
BIN="${LLAMA_SERVER:-/opt/llama.cpp/bin/llama-server}"

args=(
  -m "${MODEL_DIR}/Clef-Flash-${QUANT}.gguf"
  --host "${CLEF_HOST:-127.0.0.1}"
  --port "${CLEF_PORT:-8080}"
  -c "${CTX}" -b "${CTX}" -ub "${CTX}"
  -np 1          # Clef requests are never batched together (one joint prompt per batch), extra slots only queue
  --metrics      # Prometheus metrics on /metrics
)

if command -v nvidia-smi >/dev/null && nvidia-smi -L >/dev/null 2>&1; then
  args+=(-ngl 999)   # all layers on the GPU
fi
if [[ -n "${CLEF_THREADS:-}" ]]; then
  args+=(-t "${CLEF_THREADS}")
fi

exec "${BIN}" "${args[@]}" "$@"
