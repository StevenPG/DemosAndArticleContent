# The Ultimate Spring Boot 4 Actuator Demo

A single Spring Boot **4.0.x** application (Java 25, Gradle) wired up to exercise as
many **Spring Boot Actuator** features as possible. It is a deliberate mirror of the
[`../spring-boot-3-actuator`](../spring-boot-3-actuator) project so you can diff the
two and see exactly what changed in the move to Spring Boot 4.

> A side-by-side comparison of the two projects is in the [parent README](../README.md).

---

## Running it

```bash
# 1. Start the supporting infrastructure (Postgres, Prometheus, Grafana, Tempo)
docker compose up -d

# 2. Run the app (Gradle will provision a Java 25 toolchain via the foojay resolver)
./gradlew bootRun
```

The app starts on <http://localhost:8080>. Actuator lives under `/actuator`.

Demo credentials for the secured endpoints: **`admin` / `admin`**.

> Note: the `health` endpoint is intentionally re-mapped to **`/actuator/healthz`**
> via `management.endpoints.web.path-mapping` to demonstrate that feature.

### Generate some traffic

```bash
curl localhost:8080/api/greeting
curl localhost:8080/api/widgets
curl -X POST localhost:8080/api/widgets -H 'Content-Type: application/json' \
     -d '{"name":"Flux Capacitor","color":"silver"}'
```

### Try everything at once

Two helper scripts make it easy to exercise the whole actuator surface:

```bash
./generate-traffic.sh   # drives the business API so metrics/httpexchanges/caches/audit have data
./test-actuator.sh      # calls EVERY actuator endpoint and prints each response with a descriptor
```

Both honour `BASE_URL`, `USER`, `PASS` env vars (and `test-actuator.sh` also
`MAXLINES` for output length and `INCLUDE_SHUTDOWN=1` to additionally call
`/shutdown`). JSON is pretty-printed when `jq` is installed.

---

## What's different from Spring Boot 3?

The single most important runtime change you'll notice is the **endpoint access
model**. Spring Boot 4 **removes** the per-endpoint `management.endpoint.<id>.enabled`
flag (deprecated in 3.4) in favour of an `access` level:

```yaml
management:
  endpoints:
    access:
      default: read-only          # global default: none | read-only | unrestricted
  endpoint:
    shutdown:
      access: unrestricted        # was: management.endpoint.shutdown.enabled: true
    loggers:
      access: unrestricted        # write operations need "unrestricted"
```

Several **packages and modules also moved** as Spring Boot 4 split the monolithic
actuator into finer-grained modules. The most visible ones in this project:

| Type | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| `HealthIndicator`, `Health`, `Status`, `AbstractHealthIndicator` | `org.springframework.boot.actuate.health` | `org.springframework.boot.health.contributor` |
| `HealthEndpoint` | `org.springframework.boot.actuate.health` | `org.springframework.boot.health.actuate.endpoint` |
| `EndpointRequest` (security) | `org.springframework.boot.actuate.autoconfigure.security.servlet` | `org.springframework.boot.security.autoconfigure.actuate.web.servlet` |
| `MeterRegistryCustomizer` | `org.springframework.boot.actuate.autoconfigure.metrics` | `org.springframework.boot.micrometer.metrics.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |

And a few **build/dependency** changes:

- `spring-boot-starter-aop` was **removed** — use `org.springframework:spring-aspects`
  (brings `spring-aop` + `aspectjweaver`) for the Micrometer aspects.
- The MockMvc test slice now needs `spring-boot-starter-webmvc-test`.
- Spring Boot 4 ships **Testcontainers 2.x**: artifacts are renamed
  (`testcontainers-junit-jupiter`, `testcontainers-postgresql`), the modular
  `PostgreSQLContainer` is no longer generic and lives in
  `org.testcontainers.postgresql`.
- Baselines: **Java 17+**, **Spring Framework 7**, **Jackson 3**.

`HealthIndicator` still works the same way — it now extends the new
`HealthContributor` interface, but existing implementations are source-compatible
once the imports are updated.

---

## The endpoint catalogue

All endpoints are exposed (`management.endpoints.web.exposure.include: "*"`). `health`
and `info` are public; everything else requires HTTP Basic auth as `admin`.

| Endpoint | What it shows | Try it |
|---|---|---|
| `health` (→ `/actuator/healthz`) | Aggregated health + groups + custom indicators | `curl localhost:8080/actuator/healthz` |
| `health/liveness` | Kubernetes liveness probe group | `curl localhost:8080/actuator/healthz/liveness` |
| `health/readiness` | Kubernetes readiness probe group | `curl localhost:8080/actuator/healthz/readiness` |
| `health/business` | Custom group bundling business checks | `curl -u admin:admin localhost:8080/actuator/healthz/business` |
| `info` | Build, git, java, os, process + custom contributor | `curl localhost:8080/actuator/info` |
| `metrics` | Micrometer meter names + dimensions | `curl -u admin:admin localhost:8080/actuator/metrics/http.server.requests` |
| `prometheus` | Prometheus scrape format | `curl -u admin:admin localhost:8080/actuator/prometheus` |
| `env` | Environment & property sources (sanitized) | `curl -u admin:admin localhost:8080/actuator/env` |
| `configprops` | `@ConfigurationProperties` beans (sanitized) | `curl -u admin:admin localhost:8080/actuator/configprops` |
| `beans` | Every Spring bean + dependencies | `curl -u admin:admin localhost:8080/actuator/beans` |
| `conditions` | Auto-configuration condition report | `curl -u admin:admin localhost:8080/actuator/conditions` |
| `mappings` | All request mappings | `curl -u admin:admin localhost:8080/actuator/mappings` |
| `loggers` | View/change log levels at runtime | see below |
| `threaddump` | JVM thread dump | `curl -u admin:admin localhost:8080/actuator/threaddump` |
| `heapdump` | Downloads a heap dump | `curl -u admin:admin localhost:8080/actuator/heapdump -O` |
| `httpexchanges` | Recent HTTP request/response history | `curl -u admin:admin localhost:8080/actuator/httpexchanges` |
| `caches` | Cache names & managers | `curl -u admin:admin localhost:8080/actuator/caches` |
| `scheduledtasks` | All `@Scheduled` tasks | `curl -u admin:admin localhost:8080/actuator/scheduledtasks` |
| `flyway` | Applied database migrations | `curl -u admin:admin localhost:8080/actuator/flyway` |
| `auditevents` | Security + custom audit events | `curl -u admin:admin localhost:8080/actuator/auditevents` |
| `startup` | Application start-up step timings | `curl -u admin:admin localhost:8080/actuator/startup` |
| `sbom` | Software Bill of Materials | `curl -u admin:admin localhost:8080/actuator/sbom` |
| `shutdown` | Gracefully shut the app down | `curl -u admin:admin -X POST localhost:8080/actuator/shutdown` |
| **`featureflags`** | **Custom** read/write/delete endpoint | see below |
| **`releasenotes`** | **Custom** web-only endpoint | `curl -u admin:admin localhost:8080/actuator/releasenotes` |

### Changing a log level at runtime (`loggers`)

```bash
curl -u admin:admin -X POST localhost:8080/actuator/loggers/com.example.actuator \
     -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

### Driving the custom `featureflags` endpoint

```bash
curl -u admin:admin localhost:8080/actuator/featureflags                 # read all
curl -u admin:admin localhost:8080/actuator/featureflags/beta-search     # read one
curl -u admin:admin -X POST localhost:8080/actuator/featureflags/new-checkout \
     -H 'Content-Type: application/json' -d '{"enabled":true}'           # write
curl -u admin:admin -X DELETE localhost:8080/actuator/featureflags/beta-search  # delete
```

### Software Bill of Materials (`sbom`)

The `org.cyclonedx.bom` Gradle plugin (3.x API for Spring Boot 4) generates a
CycloneDX SBOM. Spring Boot's Gradle plugin automatically embeds it in the jar at
`META-INF/sbom/application.cdx.json` and adds `Sbom-Format`/`Sbom-Location` manifest
entries, so the `sbom` endpoint serves it with no extra configuration:

```bash
curl -u admin:admin localhost:8080/actuator/sbom              # lists available SBOMs -> ["application"]
curl -u admin:admin localhost:8080/actuator/sbom/application  # the full CycloneDX document
```

> When running via `bootRun` you may need to generate the SBOM first
> (`./gradlew cyclonedxBom`); a packaged `bootJar` always contains it.

---

## Where each feature lives in the code

| Actuator concept | Class |
|---|---|
| Custom `@Endpoint` (read/write/delete + `@Selector`) | [`FeatureFlagsEndpoint`](src/main/java/com/example/actuator/actuator/FeatureFlagsEndpoint.java) |
| Custom `@WebEndpoint` (HTTP only) | [`ReleaseNotesWebEndpoint`](src/main/java/com/example/actuator/actuator/ReleaseNotesWebEndpoint.java) |
| `@EndpointWebExtension` (technology-specific behaviour) | [`FeatureFlagsWebExtension`](src/main/java/com/example/actuator/actuator/FeatureFlagsWebExtension.java) |
| Custom `HealthIndicator` (with details) | [`PaymentGatewayHealthIndicator`](src/main/java/com/example/actuator/actuator/PaymentGatewayHealthIndicator.java) |
| `AbstractHealthIndicator` + custom `Status` | [`InventoryHealthIndicator`](src/main/java/com/example/actuator/actuator/InventoryHealthIndicator.java) |
| Health groups, probes, status http-mapping | [`application.yml`](src/main/resources/application.yml) |
| Custom `InfoContributor` | [`BuildDetailsInfoContributor`](src/main/java/com/example/actuator/actuator/BuildDetailsInfoContributor.java) |
| `MeterBinder` (live business gauge) | [`BusinessMetrics`](src/main/java/com/example/actuator/actuator/BusinessMetrics.java) |
| `@Timed` / `@Counted` / hand-rolled `Counter` | [`WidgetService`](src/main/java/com/example/actuator/service/WidgetService.java) |
| `@Observed` (metric + trace span) | [`WidgetService`](src/main/java/com/example/actuator/service/WidgetService.java), [`GreetingController`](src/main/java/com/example/actuator/web/GreetingController.java) |
| `MeterFilter` + common tags + aspects | [`ObservabilityConfig`](src/main/java/com/example/actuator/config/ObservabilityConfig.java) |
| `HttpExchangeRepository` (httpexchanges) | [`ObservabilityConfig`](src/main/java/com/example/actuator/config/ObservabilityConfig.java) |
| `AuditEventRepository` + custom audit events | [`ObservabilityConfig`](src/main/java/com/example/actuator/config/ObservabilityConfig.java), [`WidgetService`](src/main/java/com/example/actuator/service/WidgetService.java), [`AuditEventLogger`](src/main/java/com/example/actuator/actuator/AuditEventLogger.java) |
| Securing endpoints with `EndpointRequest` | [`SecurityConfig`](src/main/java/com/example/actuator/config/SecurityConfig.java) |
| `@ConfigurationProperties` (configprops) | [`DemoProperties`](src/main/java/com/example/actuator/config/DemoProperties.java) |
| `startup` endpoint (`BufferingApplicationStartup`) | [`UltimateActuatorApplication`](src/main/java/com/example/actuator/UltimateActuatorApplication.java) |
| `scheduledtasks` endpoint | [`ScheduledMaintenanceTasks`](src/main/java/com/example/actuator/actuator/ScheduledMaintenanceTasks.java) |
| Caching (caches endpoint + cache metrics) | [`CacheConfig`](src/main/java/com/example/actuator/config/CacheConfig.java) |

---

## Observability stack

`docker compose up -d` brings up:

| Service | URL | Purpose |
|---|---|---|
| Postgres | `localhost:5432` | Backs the `db`/`flyway` health & JPA metrics |
| Prometheus | <http://localhost:9090> | Scrapes `/actuator/prometheus` every 5s |
| Grafana | <http://localhost:3000> | Dashboards (anonymous admin) |
| Tempo | `localhost:3200` (OTLP on `4318`) | Receives traces from Micrometer Tracing |

---

## Tests

[`ActuatorEndpointsIntegrationTest`](src/test/java/com/example/actuator/ActuatorEndpointsIntegrationTest.java)
spins up a real Postgres with **Testcontainers 2.x** and asserts that a
representative slice of the actuator surface responds. Requires a running Docker
daemon.

```bash
./gradlew test
```
