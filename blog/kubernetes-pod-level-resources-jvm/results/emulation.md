# Docker emulation of pod-level cgroups - results

Host cgroup v1, kernel 6.18.44-fc-v37, Docker 29.3.1, app JVM flags `-XX:MaxRAMPercentage=75`,
busy sidecar holds 384 MiB in tmpfs (charged to its cgroup, as an in-memory emptyDir would be).

## `container-limits`

- pod cgroup memory: `max`, app `--memory 1g --cpus 1`, sidecar `--memory 64m`, sidecar busy: no
- outcome: **Java OutOfMemoryError (clean, catchable)** (docker OOMKilled flag: false)
- last allocation line: `outcome=java.lang.OutOfMemoryError allocatedMiB=688`

```
container=app
java=25.0.4+7-LTS
availableProcessors=1
maxHeapMiB=742
jvmSeesMemoryMiB=1024
gc=Copy+MarkSweepCompact
cgroup=v1
cgroupMemoryMax=1073741824
cgroupCpuMax=100000
```

## `pod-level-only`

- pod cgroup memory: `1073741824`, app `--memory 1g --cpus 1`, sidecar `--memory 1g`, sidecar busy: no
- outcome: **Java OutOfMemoryError (clean, catchable)** (docker OOMKilled flag: false)
- last allocation line: `outcome=java.lang.OutOfMemoryError allocatedMiB=688`

```
container=app
java=25.0.4+7-LTS
availableProcessors=1
maxHeapMiB=742
jvmSeesMemoryMiB=1024
gc=Copy+MarkSweepCompact
cgroup=v1
cgroupMemoryMax=1073741824
cgroupCpuMax=100000
```

## `pod-level-only-busy-sidecar`

- pod cgroup memory: `1073741824`, app `--memory 1g --cpus 1`, sidecar `--memory 1g`, sidecar busy: yes
- outcome: **killed by the kernel (exit 137, no Java stack trace)** (docker OOMKilled flag: true)
- last allocation line: `allocatedMiB=512`

```
container=app
java=25.0.4+7-LTS
availableProcessors=1
maxHeapMiB=742
jvmSeesMemoryMiB=1024
gc=Copy+MarkSweepCompact
cgroup=v1
cgroupMemoryMax=1073741824
cgroupCpuMax=100000
```

## `pod-level-plus-app-limit-busy-sidecar`

- pod cgroup memory: `1073741824`, app `--memory 640m --cpus 1`, sidecar `--memory 1g`, sidecar busy: yes
- outcome: **Java OutOfMemoryError (clean, catchable)** (docker OOMKilled flag: false)
- last allocation line: `outcome=java.lang.OutOfMemoryError allocatedMiB=416`

```
container=app
java=25.0.4+7-LTS
availableProcessors=1
maxHeapMiB=464
jvmSeesMemoryMiB=640
gc=Copy+MarkSweepCompact
cgroup=v1
cgroupMemoryMax=671088640
cgroupCpuMax=100000
```

