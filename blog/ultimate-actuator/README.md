# Ultimate Spring Boot Actuator

Two companion demo projects that show, in depth, what **Spring Boot Actuator** can do —
once on **Spring Boot 3** and once on **Spring Boot 4** — so you can compare the two
generations side by side. These projects are the source material for the
"Ultimate Spring Boot Actuator" blog post.

| Project | Spring Boot | Java | Build |
|---|---|---|---|
| [`spring-boot-3-actuator`](spring-boot-3-actuator) | 3.5.x | 25 | Gradle (Kotlin DSL) |
| [`spring-boot-4-actuator`](spring-boot-4-actuator) | 4.0.x | 25 | Gradle (Kotlin DSL) |

Both apps are intentionally identical in behaviour so the **only** differences you see
are the ones Spring Boot 4 actually introduced.

> **Java 25 toolchain:** both builds declare a Java 25 toolchain and use the
> [foojay resolver](https://github.com/gradle/foojay-toolchains) so Gradle will
> auto-download a matching JDK if your machine doesn't have one.

---

## Features demonstrated

Both projects exercise the same broad surface of Actuator:

**Built-in endpoints:** `health` (+ liveness/readiness/custom groups & probes),
`info` (build, git, java, os, process, custom), `metrics`, `prometheus`, `env`,
`configprops`, `beans`, `conditions`, `mappings`, `loggers` (runtime level changes),
`threaddump`, `heapdump`, `httpexchanges`, `caches`, `scheduledtasks`, `flyway`,
`auditevents`, `startup`, `sbom`, `shutdown`.

**Custom extensions:**

- A custom `@Endpoint` with `@ReadOperation` / `@WriteOperation` / `@DeleteOperation`
  and `@Selector` (`featureflags`).
- A custom `@WebEndpoint` (`releasenotes`).
- An `@EndpointWebExtension` that adds HTTP-specific behaviour to an existing endpoint.
- Two custom health indicators (a plain `HealthIndicator` and an
  `AbstractHealthIndicator` introducing a custom `DEGRADED` status), wired into
  health **groups** and **probes** with custom status → HTTP mappings.
- A custom `InfoContributor`.

**Metrics & observability:**

- Hand-rolled `Counter` / `Gauge`, declarative `@Timed` / `@Counted`, a `MeterBinder`,
  a `MeterFilter`, and common tags.
- `@Observed` for combined metrics + tracing.
- Micrometer Tracing → OpenTelemetry → OTLP export to Tempo, with `traceId`/`spanId`
  in logs.
- Prometheus scrape endpoint + a docker-compose Prometheus/Grafana/Tempo stack.

**Production concerns:**

- All actuator endpoints secured with Spring Security via `EndpointRequest`
  (health/info public, the rest admin-only).
- Custom audit events flowing into the `auditevents` endpoint.
- A real Postgres (docker-compose + Testcontainers) so the `db`/`flyway` health
  indicators and JPA/connection-pool metrics report genuine data.

See each project's README for the full endpoint catalogue, runnable `curl` examples,
and a table mapping every feature to the exact class that implements it.

---

## Spring Boot 3 → Spring Boot 4: what changed

Porting the identical app from Spring Boot 3 to 4 surfaced a handful of concrete,
copy-pasteable differences. These are the highlights:

### 1. Endpoint access model replaces `enabled`

Spring Boot 4 removes the per-endpoint `management.endpoint.<id>.enabled` flag
(deprecated since 3.4) in favour of an `access` level — `none`, `read-only`, or
`unrestricted` — plus a global `management.endpoints.access.default`.

```yaml
# Spring Boot 3
management.endpoint.shutdown.enabled: true

# Spring Boot 4
management.endpoints.access.default: read-only
management.endpoint.shutdown.access: unrestricted
```

### 2. The actuator was split into modules (packages moved)

| Type | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| `HealthIndicator`, `Health`, `Status`, `AbstractHealthIndicator` | `org.springframework.boot.actuate.health` | `org.springframework.boot.health.contributor` |
| `HealthEndpoint` | `org.springframework.boot.actuate.health` | `org.springframework.boot.health.actuate.endpoint` |
| `EndpointRequest` (servlet security) | `org.springframework.boot.actuate.autoconfigure.security.servlet` | `org.springframework.boot.security.autoconfigure.actuate.web.servlet` |
| `MeterRegistryCustomizer` | `org.springframework.boot.actuate.autoconfigure.metrics` | `org.springframework.boot.micrometer.metrics.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |

(Endpoint annotations, `InfoContributor`/`Info`, audit, and `httpexchanges` types kept
their packages.)

### 3. Dependency / build changes

- `spring-boot-starter-aop` was **removed** → use `org.springframework:spring-aspects`.
- MockMvc test slices now require `spring-boot-starter-webmvc-test`.
- Spring Boot 4 ships **Testcontainers 2.x**: renamed artifacts
  (`testcontainers-junit-jupiter`, `testcontainers-postgresql`) and a non-generic
  `PostgreSQLContainer` in `org.testcontainers.postgresql`.
- **CycloneDX SBOM:** Spring Boot 3.5 integrates with the CycloneDX Gradle plugin
  **2.x** API (`CycloneDxTask`); Spring Boot 4 integrates with the **3.x** API
  (`CyclonedxAggregateTask`). Both auto-embed the SBOM at
  `META-INF/sbom/application.cdx.json` for the `sbom` endpoint.
- New baselines: **Java 17+**, **Spring Framework 7**, **Jackson 3**.

The actual diffs are visible by comparing the two projects file-for-file — each
SB4-specific change carries an inline comment explaining the difference.

---

## Quick start

```bash
cd spring-boot-3-actuator   # or spring-boot-4-actuator
docker compose up -d        # Postgres + Prometheus + Grafana + Tempo
./gradlew bootRun           # app on http://localhost:8080

curl localhost:8080/actuator/healthz
curl -u admin:admin localhost:8080/actuator/featureflags
```
