# Java 28 AOT Code Compilation (JEP 544) — Startup & Warmup Benchmarks

Companion project for the blog post
[Java 28 AOT Code Compilation (JEP 544): Is the JIT Warmup Tax Finally Dead?](https://stevenpg.com/posts/java-28-aot-code-compilation-jep-544/)
— the next step after
[Java 26 AOT Cache with ZGC](https://stevenpg.com/posts/java-26-aot-cache-zgc-leyden-benchmarks/) and
[Project Leyden vs GraalVM Native Image](https://stevenpg.com/posts/project-leyden-vs-graalvm-native-image/).

[JEP 544](https://openjdk.org/jeps/544) (JDK 28) makes the training run store **compiled native
code** in the AOT cache, next to the loaded/linked classes (JEP 483), heap objects (JEP 516) and
method profiles (JEP 515) it already held. Production runs start executing optimized code
immediately instead of interpreting while C1/C2 catch up.

This project measures what that buys you, on three very different workloads:

| App | What it is | What we measure |
|---|---|---|
| `apps/spring-rest` | Spring Boot 4.1: MVC + JPA/Hibernate + H2 + validation + actuator, with DB-shaped *and* CPU-shaped endpoints | time to `ApplicationReadyEvent`; throughput and p99 for every second of the first 120 s under load |
| `apps/warmup-cpu` | Plain Java, zero dependencies. `adsb`: decode 200k ADS-B position reports into tracks. `javac`: compile 50 source files in-process (a reproduction of the JEP's own benchmark) | time of each iteration of identical work, 1…N |
| `apps/cli-tool` | picocli CLI that summarizes a 20k-row flight log and exits | total runtime of one invocation |

…against these configurations:

| Config | Meaning |
|---|---|
| `28:g1:none` | JDK 28, no AOT cache — the plain JIT baseline |
| `25:g1:cache`, `26:g1:cache`, `27:g1:cache` | The AOT cache as it existed in JDK 25 / 26 / 27 |
| `28:g1:cache` | JDK 28 cache with AOT code loading **disabled** (`-XX:-AOTCodeCaching`) |
| `28:g1:code` | JDK 28 cache with **JEP 544 AOT code** — same cache file as above |
| `native` | GraalVM native image (Oracle GraalVM 25) |

`28:g1:cache` vs `28:g1:code` use the *same* `.aot` file; the only difference is whether the
JVM may install the compiled code in it. That isolates exactly what JEP 544 adds.

## Requirements

- Linux x64 or AArch64 (AOT code is CPU-specific; `taskset` is used for CPU pinning)
- JDK 25, 26, 27 and a JDK 28 build (EA: <https://jdk.java.net/28/>). Paths default to
  `/opt/jdks/jdk{25,26,27,28}`; override with `JDK_HOME_25=...` etc.
- Oracle GraalVM for JDK 25 at `/opt/jdks/graal25` (or `GRAALVM_HOME=...`) for the native baselines
- Go 1.24+ (load generator), [hyperfine](https://github.com/sharkdp/hyperfine), Python 3 + matplotlib (charts)

## Quick start

```bash
JAVA_HOME=/opt/jdks/jdk25 ./gradlew build   # all apps compile to Java 25 bytecode: one jar, every JDK
./scripts/prepare.sh                         # extract the Boot jar, generate CLI input, build loadgen
./scripts/native-build.sh                    # optional: GraalVM native baselines (~10 min)
./scripts/run-all.sh                         # every experiment (~3-4 h on 4 cores)
python3 charts/plot.py                       # charts/*.svg + results/summary.md
```

Or one thing at a time:

```bash
./scripts/train.sh spring-rest 28:g1                          # build the cache (training run)
./scripts/startup.sh spring-rest 28:g1:none 28:g1:cache 28:g1:code
TAG=try DURATION=60 REPS=1 ./scripts/curve.sh 28:g1:none 28:g1:code
TAG=try MODE=javac ITERATIONS=20 ./scripts/batch.sh 28:g1:none 28:g1:code:javac   # needs: train.sh warmup-cpu 28:g1:code:javac
```

## How it works

### Training (`scripts/train.sh`)

One JVM invocation with `-XX:AOTCacheOutput=<file>` (the JEP 514 one-step workflow). The JVM runs
the workload, then at exit writes the cache — on JDK 28 that includes compiling the hot methods
it observed with C2 and storing the code.

- **spring-rest:** start the app, drive it with the load generator for 30 s, `SIGTERM` it.
- **warmup-cpu:** 10 iterations of the chosen workload (`adsb` or `javac`).
- **cli-tool:** one `summarize` invocation.

There is one cache per (app, JDK, GC, training workload). AOT code is only used when the
production run uses the **same GC** and the **same CPU features** as the training run, so the GC is
part of the cache key.

### Verifying the cache is really used (`verify_config` in `scripts/lib.sh`)

Every benchmark script refuses to measure a config until it has proven, positively, that:

1. the cache mapped (`-Xlog:aot` shows `Mapped static region`, and no `[error][aot]`), and
2. for `code`, compiled methods were actually **installed from the cache** —
   `-XX:+PrintCompilation` marks those with an `A` attribute (`AP` = preloaded at startup).

Note that `Loaded N AOT code entries from AOT Code Cache` is **not** proof: it is logged even with
`-XX:-AOTCodeCaching`, because the code region is still mapped and indexed — just never used.

On JDK 28 production runs also use `-XX:AOTMode=required` (new in 28, replacing `on`) so a
stale or mismatched cache is a hard error instead of a silent, uncached run.

### Startup (`scripts/startup.sh`)

hyperfine, 3 warmup runs + 20 measured runs, app pinned with `taskset`. Spring Boot is measured
**exec → `ApplicationReadyEvent`**: with `-Dbench.exitOnReady=true` the app calls
`Runtime.halt(0)` the moment it is ready, so the number is plain process wall time with no HTTP
polling jitter and no shutdown cost. The CLI is measured exec → exit.

### Warmup curves (`scripts/curve.sh` + `loadgen/`)

The Go load generator (closed loop, 8 connections, pinned to different cores than the server)
starts firing the instant the JVM is launched and buckets every completed request by
**seconds since `java` was launched**. One curve therefore shows startup (zero throughput),
warmup (the ramp) and steady state. Go on purpose: a Java client would be warming up its own
JIT during the exact window being measured.

### Batch warmup (`scripts/batch.sh`)

`warmup-cpu` prints the duration of every iteration of identical work. Iteration 1 is cold,
the tail is steady state. Every configuration must print the same `CHECKSUM`.

## Layout

```
apps/            spring-rest, warmup-cpu, cli-tool (Gradle multi-project, Java 25 bytecode)
loadgen/         Go closed-loop HTTP load generator, per-second CSV output
scripts/         lib.sh (configs, cache paths, verification), train/startup/curve/batch, run-all
results/raw/     every raw result file (hyperfine JSON, per-second and per-iteration CSVs)
results/summary.md   generated tables
charts/          plot.py and the generated SVGs
```

## Results

See `results/summary.md` and `results/environment.txt` (exact JDK builds and hardware) — and the
blog post for the discussion.
