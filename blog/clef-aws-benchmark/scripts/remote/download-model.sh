#!/usr/bin/env bash
# Download Clef-Flash GGUF files from Hugging Face.
#
# ggml-org/Clef-Flash-GGUF is the conversion made alongside the llama.cpp PR. It contains the
# backbone AND the joint schema head; backbone-only GGUFs from other uploaders will load but cannot
# answer /v1/systemone.
#
#   Q4_K_M   6.5 GB
#   Q8_0     9.7 GB
#   BF16    18.2 GB
#
# Usage: ./download-model.sh Q4_K_M [Q8_0 BF16 ...]
set -euo pipefail

REPO="${CLEF_HF_REPO:-ggml-org/Clef-Flash-GGUF}"
MODEL_DIR="${CLEF_MODEL_DIR:-/opt/models}"
mkdir -p "${MODEL_DIR}"

auth=()
if [[ -n "${HF_TOKEN:-}" ]]; then
  auth=(-H "Authorization: Bearer ${HF_TOKEN}")
fi

for quant in "${@:-Q4_K_M}"; do
  file="Clef-Flash-${quant}.gguf"
  dest="${MODEL_DIR}/${file}"
  if [[ -s "${dest}" ]]; then
    echo "have ${dest}"
    continue
  fi
  echo "downloading ${REPO}/${file}"
  start=$(date +%s)
  # -C - resumes a partial download; --retry covers the odd Hugging Face CDN hiccup
  curl -fL --retry 5 --retry-delay 5 -C - "${auth[@]}" -o "${dest}.part" \
    "https://huggingface.co/${REPO}/resolve/main/${file}"
  mv "${dest}.part" "${dest}"
  echo "${file}: $(du -h "${dest}" | cut -f1) in $(( $(date +%s) - start ))s"
done
