# Reference output

The run behind the numbers in `results.md`, `results/raw.json` and the blog post.

## Machine

| | |
|---|---|
| Host | Shared cloud container, 4 vCPU Intel Xeon @ 2.10GHz, 16GB RAM |
| Kernel | Linux 6.18.44 x86_64 |
| OS | Ubuntu 24.04.4 LTS |
| Docker | 29.3.1, BuildKit, containerd image store |
| JDK in the images | Temurin 25.0.4+7 LTS (`eclipse-temurin:25-*`) |
| Spring Boot | 4.1.0 |
| Gradle | 9.3.0 |
| Container limits per run | `--memory 1g --cpus 2` |
| Date | 2026-09-15 |

This is a modest shared box, not a workstation. Absolute numbers will be better on real
hardware; the relative differences are what the benchmark is about.

## How the run was driven

```bash
cd bench-app && ./gradlew bootJar && cd ..
./scripts/seed-builder-image.sh                 # bake ~/.gradle into jarbench:builder
python3 scripts/benchmark.py build --offline    # cold build + rebuild, all 10 variants
python3 scripts/benchmark.py measure --runs 5 --load-seconds 20 --threads 16
python3 scripts/benchmark.py report
```

`09-jlink-alpine` and `10-jlink-distroless` were built in a second invocation
(`--only 09-jlink-alpine,10-jlink-distroless`) after a `docker builder prune`, because the
first pass ran the container out of disk mid-build. Both are `--no-cache` cold builds
either way, so the numbers are comparable.

## Application under test

```
BENCH_SEEDED_ROWS=600 domains=24
BENCH_READY_MS=7817 revision=dev
BENCH_LOADED_CLASSES=18686
```

- 250 application source files, 24 domain packages
- 91 dependency jars, 60MB fat jar (498 entries)
- ~19,000 classes loaded by the time `/actuator/health` answers

## Cache engagement checks

Both caches were verified to actually map at runtime rather than silently falling back.

```
$ docker run --rm --entrypoint java jarbench:06-extracted-aot \
    -Xlog:aot -XX:AOTCache=/app/app.aot -Dspring.context.exit=onRefresh -jar /app/app.jar
[0.004s][info][aot] trying to map /app/app.aot
[0.004s][info][aot] Opened AOT cache /app/app.aot.
[0.004s][info][aot] Mapped static  region #0 at base 0x0000000066001000 ...

$ docker run --rm --entrypoint java jarbench:05-extracted-cds \
    -Xshare:on -Xlog:cds -XX:SharedArchiveFile=/app/app.jsa -Dspring.context.exit=onRefresh -jar /app/app.jar
[0.004s][info][cds] Opened shared archive file /app/app.jsa.
[0.004s][info][cds] Mapped static  region #0 at base 0x0000000027001000 ...
```

Variant 08 was checked the same way against `/opt/java/bin/java`, the jlink runtime, which
is the binary that trained its cache.

## Trained cache sizes inside the images

```
/app/app.aot   132,259,840 bytes   (variant 06 and 08)
/app/app.jsa   102,916,096 bytes   (variant 05)
```

## Throughput variance check

Throughput differences are small enough to be worth a sanity check, so two variants were
re-run three times each outside the main loop:

| Variant | rep 0 | rep 1 | rep 2 |
|---|---|---|---|
| `01-fatjar-jdk` | 782.5 rps / p99 86.2ms | 804.6 rps / p99 81.1ms | 768.7 rps / p99 84.8ms |
| `08-jlink-extracted-aot` | 985.7 rps / p99 74.8ms | 1033.3 rps / p99 72.0ms | 969.0 rps / p99 76.6ms |

Within-variant spread is about ±3%, and the gap between the two is ~25%, so the AOT
variants really are faster over a 20 second window that starts ~3 seconds after the app is
ready. That window is warm-up territory, not steady state - the AOT cache is saving class
loading and linking work that the baseline is still doing while serving traffic. Do not
read it as a permanent throughput win.

## Known caveats

- The load generator is Python and runs on the same 4-core box as the container under
  test, so absolute throughput is understated. It is identical for every variant, so the
  comparison holds.
- `docker stats` memory is the container's memory usage (RSS plus page cache attributable
  to the cgroup), not JVM heap. Variants that mmap a large CDS/AOT archive can read lower
  here than their true memory footprint suggests.
- Build times were measured with dependencies pre-resolved (`--offline` against
  `jarbench:builder`). A first build on a fresh machine also pays for ~90 jar downloads.
