# Java 26 AOT Cache Startup Benchmark — Any GC, Including ZGC (JEP 516)

Companion project for the blog post
[Java 26 AOT Cache with ZGC: Leyden Startup Benchmarks, Revisited](https://stevenpg.com/posts/java-26-aot-cache-zgc-leyden-benchmarks/)
(itself a follow-up to
[Project Leyden vs GraalVM Native Image](https://stevenpg.com/posts/project-leyden-vs-graalvm-native-image/)).

JEP 516 (JDK 26) made Project Leyden's AOT **object** cache GC-agnostic —
archived object references are stored as logical indices instead of physical
addresses, so ZGC (colored pointers, incompatible with the old G1-shaped
archive) finally gets the full startup win. This project measures it:

- one Spring Boot 4.1 app (webmvc + JPA/Hibernate + H2 + actuator, ~20k classes —
  deliberately app-shaped, not hello-world)
- startup measured as **time to readiness** (actuator probe returns 200),
  not the Boot banner's self-reported time
- full matrix: {G1, ZGC, Serial} × {no cache, AOT cache}, 10 runs each, median

## Requirements

- **JDK 26+** on `PATH` as `java` (JEP 516 — earlier JDKs run the app fine but
  ZGC only gets the class-loading cache layer, which is the "before" picture)
- Nothing else. H2 is in-memory; there's no external infrastructure.

## Quick start

```bash
cd bench-app && ./gradlew bootJar && cd ..   # build the app once

./scripts/train.sh                           # training run -> app.aot
./scripts/run-matrix.sh                      # full GC x cache matrix, medians
```

Or measure one configuration by hand:

```bash
./scripts/measure-startup.sh "-XX:+UseZGC -XX:AOTCache=app.aot"
./scripts/measure-startup.sh "-XX:+UseZGC"
```

## How the pieces work

### Training run (`scripts/train.sh`)

JDK 25+ ergonomics (JEP 514): a single flag creates the cache.

```bash
java -XX:AOTCacheOutput=app.aot \
     -Dspring.profiles.active=training \
     -jar bench-app/build/libs/bench-app.jar
```

The `training` profile activates a `CommandLineRunner` that exercises startup
the way production would — hits the readiness probe and the two hottest REST
paths through the real HTTP stack — then exits cleanly. Object caching rewards
a training run that looks like real startup + early traffic.

### Measurement (`scripts/measure-startup.sh`)

A wrapper that starts the JVM, polls `/actuator/health/readiness` with curl
every 5ms, and reports `ready_ms` from process start to the first 200. The
Boot log's "Started Application in X seconds" understates real readiness
(context refresh ≠ serving traffic), so we don't use it. Each configuration
runs 11 times; the first run is discarded (OS page cache priming) and the
median plus min/max of the remaining 10 is reported. RSS is sampled at
readiness for the memory table.

### Verifying the cache actually engaged

A version-mismatched or stale cache is **silently ignored** — the app runs
correctly but slow, the worst kind of regression. The measure script asserts
cache engagement by running with `-Xlog:aot` and failing loudly if the log
says the cache was disabled. Do the same in your deployment smoke tests.

## Things to notice in the results

1. **JDK 26 + ZGC + cache** should land in the same improvement band as G1
   (~40% for Spring-PetClinic-shaped apps, per the JEP) — that row is the
   entire point of JEP 516.
2. The **training GC and production GC no longer need to match**: `train.sh`
   uses default G1; the matrix consumes the same `app.aot` under all three
   collectors. One cache artifact per app version.
3. Regenerate the cache **on every build** — it's tied to the exact classpath.
   Bake `app.aot` into the container image next to the jar.
