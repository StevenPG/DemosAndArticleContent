# Valhalla value classes with JPA / Hibernate (JDK 28 EA)

Companion project for the article
[Do Java Valhalla Value Classes Make JPA and Hibernate Faster? (JDK 28 EA, Tested)](https://stevenpg.com/posts/valhalla-value-classes-jpa-hibernate).

Spring Boot 4.1.2 (Hibernate 7.4.13.Final, Spring Data JPA), PostgreSQL 18 via
Testcontainers, compiled and run on JDK 28 early-access with `--enable-preview`.

## What's in here

| Test | What it checks |
|---|---|
| `ValueClassMappingTest` | Value records as `@Embeddable`, as an `@EmbeddedId`, behind an `AttributeConverter`, through a Spring Data repository: round trip, derived queries, dirty checking, persistence-context identity, lazy proxies. All pass. |
| `RiskyMappingsTest` | Mappings that may not work. Each one bootstraps its own `SessionFactory` and prints `WORKS` or `FAILS` with the root cause: a value class as an `@Entity`, a non-record value class embeddable (with a no-arg constructor, and with `@Instantiator`), and a value record as an `@IdClass`. |
| `HydrationBenchmarkTest` | 200,000 rows, the same columns mapped two ways (value records vs. plain records). Measures load time and bytes allocated, then a pass over the loaded entities in memory. Tagged `benchmark`, so it doesn't run with `test`. |

## Running

You need Docker and a JDK 28 EA build from <https://jdk.java.net/28/>
(this was run on `28-ea+23`). Gradle itself can run on any JDK 17+. JDK 28
is only the toolchain:

```bash
export JDK28=/path/to/jdk-28.jdk/Contents/Home

# Mapping tests (about 30 s)
./gradlew test -Porg.gradle.java.installations.paths=$JDK28

# Benchmark, once
./gradlew benchmark -Porg.gradle.java.installations.paths=$JDK28

# Benchmark in three configurations (default twice, then field flattening off)
./run-benchmarks.sh build/bench-results
```

To see whether the JVM flattened a value field into an entity:

```bash
$JDK28/bin/java --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout ...
```

## Results

The raw output from the runs used in the article is in
[`results/2026-11-08-m3-pro.txt`](results/2026-11-08-m3-pro.txt). Short version:

- Load time: no measurable difference.
- Allocation: value records allocate **16 B/row more** when field flattening is on,
  and exactly the same when it's off.
- In-memory pass: results flip between JVM runs, so this harness can't measure it.
  Use JMH for that question.
