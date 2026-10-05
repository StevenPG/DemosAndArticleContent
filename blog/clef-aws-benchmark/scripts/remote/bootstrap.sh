#!/usr/bin/env bash
# First-boot setup for one benchmark instance. Runs as root from cloud-init (see
# terraform/user_data.sh.tftpl), which has already synced this repo's scripts/ and bench/ from S3
# into /opt/clef-bench and written /etc/clef-bench.env.
#
#   1. arm the safety-net shutdown
#   2. OS packages, llama.cpp build, Python venv, model downloads (each phase timed)
#   3. run the benchmark (AUTO_RUN=true) and upload results to S3
#   4. leave a llama-server running on 127.0.0.1:8080 (SERVE_QUANT) for interactive calls via SSM
#
# Re-run by hand with:  sudo /opt/clef-bench/scripts/remote/bootstrap.sh
set -Eeuo pipefail  # -E: the ERR trap also fires inside functions

set -a  # export everything in the env file (the AWS CLI needs AWS_DEFAULT_REGION)
# shellcheck disable=SC1091
source /etc/clef-bench.env
set +a
export PATH="${PATH}:/snap/bin"  # snap-installed AWS CLI on the Ubuntu AMIs
ROOT=/opt/clef-bench
RESULTS_DIR="${ROOT}/results/${INSTANCE_NAME}"
S3_RESULTS="s3://${BUCKET}/results/${INSTANCE_NAME}"
LOG=/var/log/clef-bench.log
mkdir -p "${RESULTS_DIR}"
exec > >(tee -a "${LOG}") 2>&1

log() { echo "[$(date -u +%FT%TZ)] $*"; }
upload() {
  cp "${LOG}" "${RESULTS_DIR}/bootstrap.log" || true
  aws s3 sync --only-show-errors "${RESULTS_DIR}" "${S3_RESULTS}" || true
}
status() {
  echo "$1" > "${RESULTS_DIR}/STATUS"
  upload
}
trap 'log "FAILED at line ${LINENO}"; status FAILED' ERR

declare -A phase_seconds
phase() {  # phase <name> <command...>
  local name=$1 start
  shift
  log ">>> ${name}"
  start=$(date +%s)
  "$@"
  phase_seconds[${name}]=$(( $(date +%s) - start ))
  log "<<< ${name} took ${phase_seconds[${name}]}s"
}
write_phases() {
  {
    echo "{"
    echo "  \"instance_name\": \"${INSTANCE_NAME}\", \"instance_type\": \"${INSTANCE_TYPE}\","
    local first=1
    for k in "${!phase_seconds[@]}"; do
      [[ ${first} -eq 1 ]] || echo ","
      printf '  "%s": %s' "${k}" "${phase_seconds[${k}]}"
      first=0
    done
    echo
    echo "}"
  } > "${RESULTS_DIR}/phases.json"
}

# --- 1. never leave a GPU box running all weekend -------------------------------------------
if [[ "${SHUTDOWN_MINUTES:-0}" -gt 0 ]]; then
  shutdown -h "+${SHUTDOWN_MINUTES}" "clef-bench safety-net shutdown" || true
  log "safety-net shutdown armed for +${SHUTDOWN_MINUTES} minutes (cancel: sudo shutdown -c)"
fi
status RUNNING

# --- 2. setup --------------------------------------------------------------------------------
install_packages() {
  export DEBIAN_FRONTEND=noninteractive
  # cloud-init and unattended-upgrades race for the dpkg lock on first boot
  for _ in $(seq 1 60); do
    fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1 || break
    sleep 5
  done
  apt-get update -q
  apt-get install -y -q build-essential cmake git curl jq python3-venv python3-pip libssl-dev psmisc
}

python_env() {
  python3 -m venv "${ROOT}/.venv"
  "${ROOT}/.venv/bin/pip" install -q --upgrade pip
  "${ROOT}/.venv/bin/pip" install -q -r "${ROOT}/bench/requirements.txt"
}

# shellcheck disable=SC2086
download_models() { "${ROOT}/scripts/remote/download-model.sh" ${QUANTS}; }

phase packages install_packages
phase llama_cpp_build env LLAMA_CPP_TAG="${LLAMA_CPP_TAG}" "${ROOT}/scripts/remote/install-llama-cpp.sh"
phase python_env python_env
phase model_download download_models
write_phases
upload

# --- 3. benchmark ----------------------------------------------------------------------------
if [[ "${AUTO_RUN:-true}" == "true" ]]; then
  systemctl stop clef-server.service 2>/dev/null || true  # a re-run must not share port 8080
  phase benchmark env QUANTS="${QUANTS}" INSTANCE_NAME="${INSTANCE_NAME}" INSTANCE_TYPE="${INSTANCE_TYPE}" \
    BENCH_ARGS="${BENCH_ARGS:-}" RESULTS_DIR="${RESULTS_DIR}" "${ROOT}/scripts/remote/run-benchmark.sh"
  write_phases
fi

# --- 4. leave a server up for interactive use ------------------------------------------------
cat > /etc/systemd/system/clef-server.service <<EOF
[Unit]
Description=llama.cpp serving Clef-Flash ${SERVE_QUANT} on 127.0.0.1:8080
After=network-online.target

[Service]
ExecStart=${ROOT}/scripts/remote/serve.sh ${SERVE_QUANT}
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now clef-server.service
log "clef-server.service started with ${SERVE_QUANT}"

status DONE
log "bootstrap complete"
