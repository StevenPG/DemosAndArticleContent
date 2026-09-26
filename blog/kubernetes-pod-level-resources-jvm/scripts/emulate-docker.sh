#!/usr/bin/env bash
# Reproduce the cgroup layout Kubernetes builds for pod-level resources, with
# plain Docker on a Linux host - no cluster needed.
#
# Per KEP-2837, when a pod has spec.resources and a container has no limit of
# its own, the kubelet sets that container's cgroup limit *to the pod limit*,
# and the pod cgroup (the parent) enforces the budget for all containers
# together. Here that is:
#
#   /plr-<scenario>              parent cgroup, memory limit = pod limit   ("the pod")
#     <app container>            --memory = pod limit, or its own limit      ("the JVM")
#     <sidecar container>        --memory = pod limit                        ("the sidecar")
#
# Needs root on a Linux host with the cgroupfs Docker driver (cgroup v1 or v2).
# On macOS/Docker Desktop use scripts/run.sh (kind) instead.
#
#   sudo ./scripts/emulate-docker.sh
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE_JDK=eclipse-temurin:25-jdk
IMAGE_SIDECAR=busybox:1.37
JVM_OPTS="-XX:MaxRAMPercentage=75"
POD_MEM=1073741824        # 1 GiB
POD_CPU_QUOTA=100000      # 1 CPU at the default 100 ms period
HOG_MIB=384

if [[ -f /sys/fs/cgroup/cgroup.controllers ]]; then CG=v2; else CG=v1; fi

make_pod_cgroup() { # $1 = name, $2 = memory bytes or "max"
  local name=$1 mem=$2
  if [[ $CG == v2 ]]; then
    echo "+memory +cpu" > /sys/fs/cgroup/cgroup.subtree_control 2>/dev/null || true
    mkdir -p "/sys/fs/cgroup/$name"
    echo "$mem" > "/sys/fs/cgroup/$name/memory.max"
    echo "$( [[ $mem == max ]] && echo max || echo $POD_CPU_QUOTA ) 100000" > "/sys/fs/cgroup/$name/cpu.max"
    echo "+memory +cpu" > "/sys/fs/cgroup/$name/cgroup.subtree_control"
  else
    mkdir -p "/sys/fs/cgroup/memory/$name" "/sys/fs/cgroup/cpu,cpuacct/$name"
    echo "$( [[ $mem == max ]] && echo -1 || echo "$mem" )" > "/sys/fs/cgroup/memory/$name/memory.limit_in_bytes"
    echo "$( [[ $mem == max ]] && echo -1 || echo $POD_CPU_QUOTA )" > "/sys/fs/cgroup/cpu,cpuacct/$name/cpu.cfs_quota_us"
  fi
}

remove_pod_cgroup() {
  local name=$1
  if [[ $CG == v2 ]]; then rmdir "/sys/fs/cgroup/$name" 2>/dev/null || true
  else rmdir "/sys/fs/cgroup/memory/$name" "/sys/fs/cgroup/cpu,cpuacct/$name" 2>/dev/null || true; fi
}

# scenario name | pod memory ("max" = no pod-level limit) | app --memory | app --cpus | sidecar --memory | sidecar busy?
SCENARIOS=(
  "container-limits|max|1g|1|64m|no"
  "pod-level-only|$POD_MEM|1g|1|1g|no"
  "pod-level-only-busy-sidecar|$POD_MEM|1g|1|1g|yes"
  "pod-level-plus-app-limit-busy-sidecar|$POD_MEM|640m|1|1g|yes"
)

mkdir -p results
out=results/emulation.md
{
  echo "# Docker emulation of pod-level cgroups - results"
  echo
  echo "Host cgroup $CG, kernel $(uname -r), Docker $(docker version -f '{{.Server.Version}}'), app JVM flags \`$JVM_OPTS\`,"
  echo "busy sidecar holds ${HOG_MIB} MiB in tmpfs (charged to its cgroup, as an in-memory emptyDir would be)."
  echo
} > "$out"

for spec in "${SCENARIOS[@]}"; do
  IFS='|' read -r name pod_mem app_mem app_cpus side_mem busy <<< "$spec"
  parent="plr-$name"
  docker rm -f "$name-app" "$name-sidecar" >/dev/null 2>&1 || true
  make_pod_cgroup "$parent" "$pod_mem"

  if [[ $busy == yes ]]; then
    side_cmd="dd if=/dev/zero of=/hog/fill bs=1M count=$HOG_MIB 2>/dev/null; echo holding ${HOG_MIB}MiB; sleep 600"
  else
    side_cmd="sleep 600"
  fi
  docker run -d --name "$name-sidecar" --cgroup-parent="/$parent" \
    --memory "$side_mem" --memory-swap "$side_mem" --tmpfs /hog:size=512m \
    "$IMAGE_SIDECAR" sh -c "$side_cmd" >/dev/null
  [[ $busy == yes ]] && until docker logs "$name-sidecar" 2>&1 | grep -q holding; do sleep 1; done

  docker run -d --name "$name-app" --cgroup-parent="/$parent" \
    --memory "$app_mem" --memory-swap "$app_mem" --cpus "$app_cpus" \
    -e CONTAINER_NAME=app -e JAVA_TOOL_OPTIONS="$JVM_OPTS" \
    -v "$PWD/probe:/probe:ro" "$IMAGE_JDK" java /probe/ResourceProbe.java allocate >/dev/null
  docker wait "$name-app" >/dev/null
  exit_code=$(docker inspect -f '{{.State.ExitCode}}' "$name-app")
  oom_flag=$(docker inspect -f '{{.State.OOMKilled}}' "$name-app")
  logs=$(docker logs "$name-app" 2>&1)
  probe=$(echo "$logs" | grep '^container=' | head -1)
  last=$(echo "$logs" | grep -E 'allocatedMiB=' | tail -1)
  if echo "$logs" | grep -q 'outcome=java.lang.OutOfMemoryError'; then verdict="Java OutOfMemoryError (clean, catchable)"
  elif [[ $exit_code == 137 ]]; then verdict="killed by the kernel (exit 137, no Java stack trace)"
  else verdict="exit $exit_code"; fi

  echo "[$name] $verdict  | $last"
  {
    echo "## \`$name\`"
    echo
    echo "- pod cgroup memory: \`$pod_mem\`, app \`--memory $app_mem --cpus $app_cpus\`, sidecar \`--memory $side_mem\`, sidecar busy: $busy"
    echo "- outcome: **$verdict** (docker OOMKilled flag: $oom_flag)"
    echo "- last allocation line: \`$last\`"
    echo
    echo '```'
    echo "$probe" | tr ' ' '\n' | grep -v '^procSelfCgroup'
    echo '```'
    echo
  } >> "$out"

  docker rm -f "$name-app" "$name-sidecar" >/dev/null
  remove_pod_cgroup "$parent"
done
echo "wrote $out"
