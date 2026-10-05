#!/usr/bin/env bash
# Build llama-server from a pinned llama.cpp tag.
#
#   - CPU boxes: native build (-march=native picks up AVX-512/AMX on c7i, AVX-512 on c7a, SVE2 on c8g).
#   - GPU boxes: CUDA build for exactly the GPU that is installed (compute capability from nvidia-smi),
#     which keeps the build to one architecture and roughly halves compile time.
#
# Clef support landed in llama.cpp PR #29831 (first tag: b11390). Older tags load the GGUF but
# /v1/systemone returns 501 "not a decision model".
#
# Usage: LLAMA_CPP_TAG=b11401 ./install-llama-cpp.sh
set -euo pipefail

TAG="${LLAMA_CPP_TAG:-b11401}"
PREFIX="${LLAMA_CPP_PREFIX:-/opt/llama.cpp}"
SRC="${PREFIX}/src"

# A --depth 1 clone reports "build 1" in --version, so the installed tag is tracked in a marker file.
if [[ -x "${PREFIX}/bin/llama-server" && "$(cat "${PREFIX}/TAG" 2>/dev/null)" == "${TAG}" ]]; then
  echo "llama-server ${TAG} already installed"
  exit 0
fi

cmake_args=(
  -DCMAKE_BUILD_TYPE=Release
  -DGGML_NATIVE=ON
  -DBUILD_SHARED_LIBS=OFF        # one self-contained binary we can copy to ${PREFIX}/bin
  -DLLAMA_BUILD_TESTS=OFF
  -DLLAMA_BUILD_EXAMPLES=OFF
)

if command -v nvidia-smi >/dev/null && nvidia-smi -L >/dev/null 2>&1; then
  # Deep Learning Base AMIs ship the toolkit under /usr/local/cuda
  export PATH="/usr/local/cuda/bin:${PATH}"
  if ! command -v nvcc >/dev/null; then
    echo "NVIDIA GPU found but no nvcc: use the Deep Learning Base AMI or install the CUDA toolkit" >&2
    exit 1
  fi
  arch="$(nvidia-smi --query-gpu=compute_cap --format=csv,noheader | head -1 | tr -d '.')"
  echo "NVIDIA GPU detected: $(nvidia-smi --query-gpu=name --format=csv,noheader | head -1), sm_${arch}"
  cmake_args+=(-DGGML_CUDA=ON "-DCMAKE_CUDA_ARCHITECTURES=${arch}")
fi

rm -rf "${SRC}"
git clone --quiet --depth 1 --branch "${TAG}" https://github.com/ggml-org/llama.cpp "${SRC}"
cd "${SRC}"

start=$(date +%s)
cmake -B build "${cmake_args[@]}"
cmake --build build --target llama-server -j "$(nproc)"
echo "build took $(( $(date +%s) - start ))s"

install -d "${PREFIX}/bin"
install -m 0755 build/bin/llama-server "${PREFIX}/bin/llama-server"
echo "${TAG}" > "${PREFIX}/TAG"
"${PREFIX}/bin/llama-server" --version
