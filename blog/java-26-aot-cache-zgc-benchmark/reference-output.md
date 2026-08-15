# Java 26 AOT Cache + ZGC Benchmark — Reference Output

> **These timings are not the blog post's numbers.** They were captured on a shared 4-core
> x86_64 Linux container, not the M3 the post is written around. Startup times here are ~5-10x
> what a laptop produces and the poller competes with the app for cores. Use them as a
> correctness and shape reference — the matrix runs, the cache engages, the relative
> improvements land where JEP 516 says they should — and re-measure on real hardware before
> publishing anything.

Captured on 2026-08-01 with **Temurin JDK 26.0.2+10**, Gradle 9.3, Spring Boot 4.1.0,
4 vCPU / 15 GB, `postgres`-free (H2 in memory).

## 1. Build and training

```
cd bench-app && ./gradlew bootJar     # BUILD SUCCESSFUL in 40s -> bench-app.jar (52 MB)
./scripts/train.sh
```

```
==> training app.aot (default GC - compressed oops)
[training] GET /actuator/health/readiness -> 200
[training] GET /products -> 200
[training] GET /products/1 -> 200
AOTCache creation is complete: app.aot 124018688 bytes

==> training app-zgc.aot (ZGC - no compressed oops)
[training] GET /actuator/health/readiness -> 200
[training] GET /products -> 200
[training] GET /products/1 -> 200
AOTCache creation is complete: app-zgc.aot 125136896 bytes
```

`-XX:AOTCacheOutput` one-step creation (JEP 514) works as documented on JDK 26 — the JVM
records a temporary `app.aot.config`, forks a child to assemble the cache, and deletes the
config. No two-step record/create flow needed.

The cache is ~118 MiB for a 52 MB fat jar. Budget for that in a container image.

## 2. The matrix

`./scripts/run-matrix.sh` — 11 runs per row, first discarded, median of 10.
Startup is time from `java` to the first 200 from `/actuator/health/readiness`.

| GC | Cache | Median | Min | Max | RSS at readiness | vs. no cache |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| G1 | none | 9235ms | 8324ms | 11351ms | 507MB | — |
| G1 | `app.aot` | **6133ms** | 5752ms | 6731ms | 535MB | **−33.6%** |
| ZGC | none | 8285ms | 8021ms | 9043ms | 623MB | — |
| ZGC | `app-zgc.aot` | **5885ms** | 5762ms | 6492ms | 756MB | **−29.0%** |
| Serial | none | 8866ms | 8215ms | 9246ms | 282MB | — |
| Serial | `app.aot` | **6143ms** | 5742ms | 6558ms | 256MB | **−30.7%** |

ZGC with a cache is the fastest row of the six, which is the JEP 516 headline. The
improvement band (29-34%) is a little under the ~40% the JEP quotes for PetClinic, which is
what you would expect when the measurement machine is slow enough that the fixed
non-cacheable work dominates less than the noise.

## 3. The finding that matters: ZGC needs its own cache

The matrix **aborted** on the ZGC + cache row the first time, and the abort was correct:

```
=== -XX:+UseZGC -XX:AOTCache=app.aot
FATAL: AOT cache was requested but the JVM did not map it:
[0.010s][info   ][aot] trying to map app.aot
[0.010s][info   ][aot] Opened AOT cache app.aot.
[0.010s][info   ][aot] The AOT cache was created with UseCompressedOops = 1, UseCompressedClassPointers = 1, UseCompactObjectHeaders = 0
[0.010s][warning][aot] Unable to use AOT cache.
[0.010s][error  ][aot] An error has occurred while processing the AOT cache.
[0.010s][error  ][aot] Loading static archive failed.
[0.010s][error  ][aot] Unable to map shared spaces
```

JEP 516 made the *object* cache GC-agnostic — archived references became logical indices — but
the cache still records the **oop encoding** it was built with, and ZGC does not support
compressed oops. A cache trained under the default (G1) collector is therefore unusable under
ZGC, and the JVM says so and then starts anyway, uncached, at full cold-start cost. With
`-Xlog:aot` off, which is the normal case, nothing at all is printed.

So the original claim in this README — "train with G1 here, consume it under ZGC/Serial in the
matrix; one cache artifact per app version" — was half right. Serial does consume the G1-built
cache (see the table). ZGC does not. `train.sh` now produces two artifacts, and
`run-matrix.sh` hands ZGC the right one; with `app-zgc.aot` the ZGC row runs and lands at
−29%.

Practical version of the rule: **one cache artifact per oop encoding**, not per app version.
If you deploy the same jar under G1 in one environment and ZGC in another, you ship two.

## 4. The engagement check had to be rewritten

The original check grepped for `"Unable to use AOT cache"` or `"AOT cache disabled"`. The
first string does appear for the oop mismatch above, which is luck — the more common
stale-artifact case prints neither. Point JDK 26 at a truncated cache:

```
[0.008s][info   ][aot] trying to map stale-test.aot
[0.008s][info   ][aot] Opened AOT cache stale-test.aot.
[0.008s][warning][aot] The AOT cache has been truncated.
[0.008s][error  ][aot] An error has occurred while processing the AOT cache.
[0.008s][error  ][aot] Loading static archive failed.
[0.008s][error  ][aot] Unable to map shared spaces
```

No "unable to use", no "disabled" — the old grep stayed silent and the benchmark would have
reported uncached numbers in the cached column. The check is now positive: require
`Mapped static region`, reject any `[error][aot]`. Verified both directions:

- healthy `app.aot` → check passes, matrix proceeds
- truncated cache → `FATAL: AOT cache was requested but the JVM did not map it`, exit 1

## 5. Other fixes this run required

- **`bootJar` did not build.** The toolchain was pinned to Java 25 while the README asks for
  JDK 26, so Gradle failed with *"Cannot find a Java installation ... matching
  {languageVersion=25}"* on a machine with exactly the JDK the benchmark requires. Toolchain
  is now 26. Note that `gradlew` prefers `JAVA_HOME` over `PATH`, so `JAVA_HOME` has to point
  at the JDK 26 if it lives somewhere Gradle does not auto-detect.
- **The training run trained the wrong paths.** As a `CommandLineRunner` it fired before
  readiness flipped to `ACCEPTING_TRAFFIC` and before `DataLoader`'s runner (both were
  declared `@Order(Integer.MAX_VALUE)` — a tie, not an ordering), producing:

  ```
  [training] GET /actuator/health/readiness -> 503
  [training] GET /products -> 200
  [training] GET /products/1 -> 500
  ```

  So the cache was trained against the probe's failure path and a `NoSuchElementException`
  instead of the JPA read path. It now hangs off the `ACCEPTING_TRAFFIC` availability event,
  which Boot publishes after every runner has returned, and all three requests answer 200.

## 6. Measurement notes for the real run

- `measure-startup.sh` polls with a fresh `curl` process every 5ms. On 4 cores that poller is
  a meaningful competitor to the JVM it is timing; on a many-core laptop it is not. Worth
  keeping in mind when comparing numbers across machines.
- RSS at readiness is sampled from `/proc/<pid>/status` on Linux. Serial is the cheapest
  (256-282MB), ZGC the most expensive (623-756MB) — and the cache costs ZGC another ~130MB of
  RSS while saving it 2.4s, which is a trade worth stating explicitly in the post.
- The JDK 25-vs-26 ZGC comparison rows still need a JDK 25 install; not run here.
