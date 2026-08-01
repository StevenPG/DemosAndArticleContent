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
- The same JDK 26 visible to Gradle. `bootJar` uses a Java 26 toolchain, and the
  `gradlew` script prefers `JAVA_HOME` over `PATH`, so export `JAVA_HOME` if your
  JDK 26 lives somewhere Gradle does not auto-detect (SDKMAN, `/usr/lib/jvm`,
  Homebrew, `~/.gradle/jdks`). Otherwise the build stops at *"Cannot find a Java
  installation ... matching {languageVersion=26}"*.
- Nothing else. H2 is in-memory; there's no external infrastructure.

## Quick start

```bash
cd bench-app && ./gradlew bootJar && cd ..   # build the app once

./scripts/train.sh                           # training runs -> app.aot, app-zgc.aot
./scripts/run-matrix.sh                      # full GC x cache matrix, medians
```

Or measure one configuration by hand:

```bash
./scripts/measure-startup.sh "-XX:+UseZGC -XX:AOTCache=app-zgc.aot"
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

The `training` profile activates a listener that exercises startup the way
production would — hits the readiness probe and the two hottest REST paths
through the real HTTP stack — then exits cleanly. Object caching rewards a
training run that looks like real startup + early traffic.

It hangs off the readiness state flipping to `ACCEPTING_TRAFFIC`, not off a
`CommandLineRunner`. A runner fires too early on both counts:
`/actuator/health/readiness` still answers 503 at that point, and runner order
against `DataLoader`'s seeding runner is undefined, so `/products/1` can run
before a single row exists and train the 500 path instead of the read path.
A correct training run prints:

```
[training] GET /actuator/health/readiness -> 200
[training] GET /products -> 200
[training] GET /products/1 -> 200
```

Anything else means the cache was trained against something other than a
working app.

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
correctly but slow, the worst kind of regression. The measure script runs the
first (discarded) iteration with `-Xlog:aot` and fails loudly unless the cache
was really mapped. Do the same in your deployment smoke tests.

Assert engagement **positively**. Matching on failure strings does not work,
because there is no "cache disabled" line to match. Point JDK 26 at a truncated
cache and it prints

```
[warning][aot] The AOT cache has been truncated.
[error  ][aot] An error has occurred while processing the AOT cache.
[error  ][aot] Loading static archive failed.
[error  ][aot] Unable to map shared spaces
```

and then starts normally, uncached. A mapped cache always logs
`Mapped static region #0 ...`, so the script requires that line and rejects any
`[error][aot]`. Verified both ways against JDK 26.0.2: a healthy `app.aot`
passes, and a deliberately truncated one makes the script exit 1.

## Things to notice in the results

1. **JDK 26 + ZGC + cache** should land in the same improvement band as G1
   (~40% for Spring-PetClinic-shaped apps, per the JEP) — that row is the
   entire point of JEP 516.
2. The **training GC and production GC no longer need to match — but the oop
   encoding does.** JEP 516 made the object cache GC-agnostic, and a cache
   trained under G1 is consumed happily by Serial. ZGC is the exception, and
   not because of the collector: ZGC does not support compressed oops, the
   cache records the encoding it was built with, and the mismatch is fatal:

   ```
   [info   ][aot] The AOT cache was created with UseCompressedOops = 1, ...
   [warning][aot] Unable to use AOT cache.
   [error  ][aot] Unable to map shared spaces
   ```

   The JVM then starts uncached and *looks* fine, which is why the engagement
   check above matters. `train.sh` therefore builds two artifacts: `app.aot`
   for the compressed-oops collectors and `app-zgc.aot` for ZGC. "One cache
   artifact per app version" holds within one oop encoding, not across all of
   them.
3. Regenerate the cache **on every build** — it's tied to the exact classpath.
   Bake `app.aot` (and `app-zgc.aot`, if you deploy on ZGC) into the container
   image next to the jar.
