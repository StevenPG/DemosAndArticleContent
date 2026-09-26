# Java 27 Defaults Benchmark

JDK 27 changed four defaults that apply the moment you swap the base image. You
don't set a flag for any of them:

- **[JEP 534](https://openjdk.org/jeps/534)**: compact object headers are on by default. Headers are 8 bytes instead of 12.
- **[JEP 523](https://openjdk.org/jeps/523)**: G1 is the default collector everywhere. Before this, a JVM that
  saw a single CPU or less than 1792 MB picked Serial. A `--cpus 1` pod is exactly that case.
- **[JEP 527](https://openjdk.org/jeps/527)**: TLS 1.3 offers the hybrid `X25519MLKEM768` key exchange first.
- **[JEP 536](https://openjdk.org/jeps/536)**: JFR recordings redact secrets by default: environment variables,
  system properties and JVM arguments whose names look like passwords, tokens or secrets.

This project measures what those changes do to one Spring Boot app when nothing else
changes: the same jar, the same data and the same container limits. Only the `java`
binary and its defaults differ.

Accompanies the post **[Java 27 Changed Your Defaults: What Happens When You Just Bump the Base Image](https://stevenpg.com/posts/java-27-new-defaults-benchmark/)**.

## Layout

```
probes/DefaultsProbe.java     single-file: prints what the JVM picked (GC, headers, heap, TLS groups)
probes/HeaderFootprint.java   single-file: retained bytes per instance for 10 object shapes
probes/jfr-redaction.sh       records a JFR file with planted secrets and shows what got into it
bench-app/                    Spring Boot 4.1 app with a 2M-object in-memory telemetry cache
scripts/fetch-jdks.sh         downloads Temurin 25/26/27 for Docker's architecture into .jdks/
scripts/benchmark.py          runs the container matrix, writes results/raw.json + results.md
results/                      probe output and the container run
```

## Quick start: the probes

Neither probe has any dependencies. Run them with the JDK you're curious about:

```bash
java probes/DefaultsProbe.java
java -Xmx2g probes/HeaderFootprint.java
java -Xmx2g -XX:-UseCompactObjectHeaders probes/HeaderFootprint.java
```

To see the collector change, run the probe inside a small container. JDK 26 prints
`Serial` and JDK 27 prints `G1`:

```bash
./scripts/fetch-jdks.sh
docker run --rm --cpus 1 --memory 1g -v $PWD/.jdks:/jdks:ro -v $PWD/probes:/p \
  debian:trixie-slim /jdks/jdk26/bin/java /p/DefaultsProbe.java
```

## The app

It's an in-memory ADS-B style telemetry cache: 20,000 aircraft, each holding its last 100
`Position` records, for 2,000,000 retained records. They're seeded with a fixed random seed
before readiness flips, so every row holds identical data.

A `Position` has 32 bytes of fields. With a 12-byte header it's 44 bytes, padded to 48.
With an 8-byte header it's exactly 40. This is one of the shapes where compact headers
save a full 8 bytes. `results/probes.md` has shapes where they save nothing.

Three request types, mixed 70/20/10 by the load generator:

| Endpoint | What it stresses |
|---|---|
| `GET /api/aircraft/{id}/track?limit=50` | small allocation + JSON serialization |
| `POST /api/aircraft/{id}/positions` (20 positions) | allocation + eviction of old-gen objects |
| `GET /api/stats/altitude-bands` | pointer-chasing over the entire live set |

Harness-only endpoints: `GET /bench/info` (the JVM's choices), `POST /bench/live-set`
(full GC, then used heap) and `GET /bench/gc` (cumulative pause count and time).

The jar is compiled for release 25, so the same artifact runs on 25, 26 and 27.

## Running the benchmark

Requires Docker, Python 3.11+ and a JDK 25 to build with. The benchmark itself runs its JVMs
from `.jdks/`.

```bash
./scripts/fetch-jdks.sh
(cd bench-app && ./gradlew bootJar)
python3 scripts/benchmark.py measure                       # both profiles, all rows, 3 runs each
python3 scripts/benchmark.py measure --profile small --only jdk26 jdk27 --runs 1
python3 scripts/benchmark.py report                        # re-render results/results.md
```

### Profiles

| Profile | Limits | Why |
|---|---|---|
| `small` | `--cpus 1 --memory 1g` | Below JEP 523's old Serial threshold, so JDK 25/26 pick Serial and JDK 27 picks G1. |
| `medium` | `--cpus 2 --memory 2g` | Above it, so every JDK picks G1 and only the header change is in play. |

No `-Xmx` is set anywhere. The point is what the defaults do: max heap is the JVM's default
25% of the container limit.

### Rows

| Row | Flags | Isolates |
|---|---|---|
| `jdk25` | none | the LTS baseline |
| `jdk25+coh` | `-XX:+UseCompactObjectHeaders` | what you could already opt into on 25 |
| `jdk26` | none | the previous feature release |
| `jdk27` | none | the new defaults, all at once |
| `jdk27-coh` | `-XX:-UseCompactObjectHeaders` | JDK 27 with only the header change undone |
| `jdk27+serial` | `-XX:+UseSerialGC` | JDK 27 with only the collector change undone |

### Measured per row

- the collector and header layout the JVM actually chose (read back from the JVM, not assumed)
- time from `docker run` to readiness 200. This includes seeding the 2M records.
- the live set: heap used after a full GC
- container memory (cgroup) at idle and after load
- throughput, p50 and p99 over a mixed load, after a warm-up
- GC pause count and total pause time during the measured window

## Results

`results/probes.md` has the probe output. Object layout numbers are deterministic for a given
JDK and flag set, so they're valid on any host.

`results/results.md` and `raw.json` are the published run: an M3 Pro MacBook, Docker Desktop
(linux/arm64, 2 CPUs given to the Docker VM), the load generator on macOS outside the VM, and 3 runs
per row (medians shown). Headline results:

- Compact headers: 14.5% smaller live set (118.5 → 101 MiB) on every JDK that has them, and 11–13% less
  container memory under load going from `jdk26` to `jdk27`.
- 1 CPU: JDK 27's default G1 served 18.5% fewer req/s than JDK 26's Serial, with a 14% worse p99.
  `-XX:+UseSerialGC` on 27 recovered all of it.
- 2 CPUs: Serial on 27 served 37% more req/s than the G1 default, with a p99 of 51 ms against 73 ms.
- Compact headers' throughput effect ranged from +13% (G1, 1 CPU) to -12%/-20% (G1, 2 CPUs, JDK 27/25).

An earlier run on a shared 4-core x86_64 cloud container showed the same shape. It was replaced by this one.

## Notes and gotchas

- **Temurin 27 wasn't on Docker Hub yet** when this was written, so the JDKs are mounted
  into `debian:trixie-slim` rather than pulled as images. Mounting also guarantees that every
  row uses an identical userland.
- **`availableProcessors` follows `--cpus`**, and that decides JEP 523's old threshold. A
  `-XX:ActiveProcessorCount=1` on a big machine has the same effect on JDK 25/26.
- **The default TLS named groups changed in two ways on 27.** `X25519MLKEM768` is first,
  and `ffdhe6144` and `ffdhe8192` are no longer in the default list. If you talk to a peer
  that only offers large finite-field DH groups, check it before you upgrade.
- **JFR on JDK 26 and earlier writes secrets into the recording.** `probes/jfr-redaction.sh` plants
  `DB_PASSWORD`, `API_TOKEN` and `-Dapp.secret`. JDK 26 records all three in plain text, and JDK 27 records
  `[REDACTED]`, including the whole `-Dapp.secret=...` argument. Recordings that were already shipped to
  a support ticket or bucket before you upgrade still contain them.
- **"Serial" shows up as `Copy` and `MarkSweepCompact`** in the GC MXBeans. `/bench/info` reports
  the raw bean names, and the report maps them.
