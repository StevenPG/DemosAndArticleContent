#!/usr/bin/env bash
# Create a kind cluster on Kubernetes 1.37, run the four scenarios, and write
# results/results.md. Re-runnable: scenarios are deleted and re-applied.
#
#   ./scripts/run.sh              # create cluster if needed, run everything
#   KEEP_CLUSTER=0 ./scripts/run.sh   # delete the cluster afterwards
set -euo pipefail
cd "$(dirname "$0")/.."

CLUSTER=plr
if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  kind create cluster --name "$CLUSTER" --config kind-config.yaml --wait 120s
fi
CTX="kind-$CLUSTER"
k() { kubectl --context "$CTX" "$@"; }

# Pre-load images so pull time doesn't count against anything.
for image in eclipse-temurin:25-jdk busybox:1.37; do
  docker image inspect "$image" >/dev/null 2>&1 || docker pull -q "$image"
  kind load docker-image --name "$CLUSTER" "$image" >/dev/null
done

echo "== cluster"
k version -o json | python3 -c 'import json,sys; v=json.load(sys.stdin)["serverVersion"]; print("server", v["gitVersion"])'
docker exec "$CLUSTER-control-plane" sh -c 'stat -fc %T /sys/fs/cgroup' | sed 's/^/node cgroup fs: /'
k get --raw /metrics | grep -E 'kubernetes_feature_enabled\{name="(PodLevelResources|InPlacePodLevelResourcesVerticalScaling|PodLevelResourceManagers)"' || true

k create configmap resource-probe --from-file=probe/ResourceProbe.java --dry-run=client -o yaml | k apply -f - >/dev/null

mkdir -p results
out=results/results.md
{
  echo "# Pod-level resources and the JVM - results"
  echo
  echo "Server $(k version -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"])'), node cgroup filesystem \`$(docker exec "$CLUSTER-control-plane" stat -fc %T /sys/fs/cgroup)\`, node memory $(docker exec "$CLUSTER-control-plane" awk '/MemTotal/ {printf "%d MiB", $2/1024}' /proc/meminfo), node CPUs $(docker exec "$CLUSTER-control-plane" nproc)."
  echo
} > "$out"

for manifest in manifests/*.yaml; do
  name=$(awk '/^  name:/ {print $2; exit}' "$manifest")
  k delete pod "$name" --ignore-not-found --wait=true >/dev/null
  k apply -f "$manifest" >/dev/null
  # The app container either finishes (Java OOM) or is OOMKilled; either way it terminates.
  for _ in $(seq 1 120); do
    state=$(k get pod "$name" -o jsonpath='{.status.containerStatuses[?(@.name=="app")].state.terminated.reason}' 2>/dev/null || true)
    [[ -n "$state" ]] && break
    sleep 2
  done
  exit_code=$(k get pod "$name" -o jsonpath='{.status.containerStatuses[?(@.name=="app")].state.terminated.exitCode}')
  logs=$(k logs "$name" -c app 2>/dev/null || true)
  probe=$(echo "$logs" | grep '^container=' | head -1)
  last=$(echo "$logs" | grep -E 'allocatedMiB=' | tail -1)
  echo "[$name] reason=$state exit=$exit_code"
  echo "  $probe"
  echo "  last: $last"
  {
    echo "## \`$name\`"
    echo
    echo "- termination: **$state** (exit $exit_code)"
    echo "- last allocation line: \`$last\`"
    echo
    echo '```'
    echo "$probe" | tr ' ' '\n'
    echo '```'
    echo
  } >> "$out"
done

echo
echo "wrote $out"
if [[ "${KEEP_CLUSTER:-1}" == "0" ]]; then kind delete cluster --name "$CLUSTER"; fi
