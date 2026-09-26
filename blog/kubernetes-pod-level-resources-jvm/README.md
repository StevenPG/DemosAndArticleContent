# Kubernetes Pod-Level Resources and the JVM

Kubernetes [pod-level resources](https://kubernetes.io/docs/tasks/configure-pod-container/assign-pod-level-resources/)
(`spec.resources` on the Pod, beta and on by default since 1.34) let you give a whole pod one CPU and memory
budget and leave its containers without limits of their own. For sidecar-heavy pods, that's what people have
wanted for years: the app and its log shipper share 1 GiB instead of each being boxed into a guess.

The JVM sizes its heap from the memory limit it can see. So what does it see, and what happens when the sidecar
next to it is using part of the budget?

Accompanies the post **[Pod-Level Resources in Kubernetes 1.37: Your JVM Thinks It Owns the Whole Pod](https://stevenpg.com/posts/kubernetes-pod-level-resources-jvm/)**.

## The short answer

[KEP-2837](https://github.com/kubernetes/enhancements/tree/master/keps/sig-node/2837-pod-level-resource-spec)
is explicit: when a container has no limit of its own, *"the pod-level limit is applied to each container's cgroup
maximum value"*, because *"some runtimes (like the Java runtime) rely on container-level cgroup maximum values."*
So the JVM sees the **whole pod budget** as its own. So does every other container in the pod.

With the near-universal `-XX:MaxRAMPercentage=75`, the JVM plans a heap of 75% of the *pod*. If a sidecar is
holding part of that pod budget, the pod cgroup runs out before the heap does, and the kernel kills the JVM:
exit 137, no `OutOfMemoryError`, no stack trace, no heap dump.

## Layout

```
probe/ResourceProbe.java          single-file Java: what the JVM and the cgroup files say, then fill the heap
scripts/gen-manifests.py          generates manifests/ from one template (the scenarios differ only in limits)
manifests/01..04-*.yaml           the four scenarios, one Pod each: app + native-sidecar "log shipper"
scripts/run.sh                    kind cluster on Kubernetes 1.37, runs the manifests  -> results/results.md
scripts/emulate-docker.sh         the same four scenarios as nested cgroups in plain Docker -> results/emulation.md
kind-config.yaml                  single-node kind cluster on kindest/node:v1.37.0
```

## Scenarios

Every app container runs `java ResourceProbe.java allocate` on `eclipse-temurin:25-jdk` with
`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. The probe prints what the JVM decided, then allocates heap in
16 MiB steps until something stops it. A "busy" sidecar writes 384 MiB into an in-memory `emptyDir`, which is
charged to its cgroup and so to the pod's, and a `startupProbe` holds the app back until it has.

| # | Pod `spec.resources` | App container limit | Sidecar |
|---|---|---|---|
| 01 | none | 1 CPU / 1 GiB | 64 MiB limit, idle |
| 02 | 1 CPU / 1 GiB | none (so: the pod limit) | no limit, idle |
| 03 | 1 CPU / 1 GiB | none (so: the pod limit) | no limit, **holding 384 MiB** |
| 04 | 1 CPU / 1 GiB | **640 MiB** | no limit, **holding 384 MiB** |

## Results: Docker emulation (run)

`scripts/emulate-docker.sh` builds the cgroup layout the KEP describes out of plain Docker containers: a parent
cgroup carrying the pod limit (`--cgroup-parent`), each container's own limit set to the pod limit unless the
scenario gives it one, and the sidecar in the same parent. The kernel enforces the budget exactly as it would under
the kubelet. Run on cgroup v1, JDK 25.0.4, full output in `results/emulation.md`:

| # | JVM saw | Max heap | Outcome |
|---|---|---|---|
| 01 | 1024 MiB, 1 CPU | 742 MiB | `OutOfMemoryError` after 688 MiB. Clean, and the app can log it. |
| 02 | 1024 MiB, 1 CPU | 742 MiB | `OutOfMemoryError` after 688 MiB. Clean. |
| 03 | 1024 MiB, 1 CPU | 742 MiB | **Killed by the kernel after 512 MiB (exit 137).** No Java stack trace. |
| 04 | 640 MiB, 1 CPU | 464 MiB | `OutOfMemoryError` after 416 MiB. Clean, and the sidecar keeps running. |

Two side effects worth knowing:

- Every JVM here saw 1 CPU and, being JDK 25, picked **SerialGC**. That's JEP 523's old rule, and JDK 27 would pick G1.
- In 03 the JVM had no way to know. Its cgroup said 1 GiB, and it was right about its *own* cgroup.

## Results: kind on Kubernetes 1.37 (not yet run)

`scripts/run.sh` creates a kind cluster on `kindest/node:v1.37.0`, runs the same four manifests, and records the
JVM's view, the cgroup files and the pod's termination reason. **It hasn't been run yet.** The cloud container
this was built in can't lower `oom_score_adj` even as root, so every pod sandbox failed with `runc` "failed to
update /proc/self/oom_score_adj: Permission denied". That rules out kind, k3s and minikube in that environment.
It needs Docker Desktop, or any Linux host with a normal Docker.

```bash
./scripts/run.sh                    # creates the cluster if needed, runs all four scenarios
KEEP_CLUSTER=0 ./scripts/run.sh     # ...and deletes it afterwards
```

The expected outcome is the emulation table. The things only a real cluster can confirm are:

- whether the kubelet sets the app container's `memory.max` to the pod limit, as the KEP says
- on cgroup v2 (Docker Desktop), whether the kernel OOM-kills the JVM in 03, or whether the sidecar goes first
- the `PodLevelResources` feature stage, which `run.sh` prints from the `kubernetes_feature_enabled` metric

## Fixes, in order of preference

1. **Give the JVM container its own limit** (scenario 04). Keep the pod-level budget for the sidecars. The JVM
   sizes itself from its own limit, and the sidecars burst into whatever it leaves.
2. **Or size the heap in absolute terms**, not as a percentage: `-Xmx` or `-XX:MaxRAM`, leaving room for the
   sidecars' working set.
3. **Or lower `MaxRAMPercentage`** for pods that use pod-level resources. That's the weakest fix, because the right
   number depends on the neighbors.

## Run the emulation yourself

Linux only, as root, with the Docker cgroupfs driver (cgroup v1 or v2):

```bash
sudo ./scripts/emulate-docker.sh
```
