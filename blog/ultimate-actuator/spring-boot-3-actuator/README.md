# The Ultimate Spring Boot 3 Actuator Demo

A single Spring Boot **3.5.x** application (Java 25, Gradle) wired up to exercise as
many **Spring Boot Actuator** features as possible. Every feature lives in its own
clearly-named class so it is easy to point at exactly one place per concept.

> Looking for the Spring Boot 4 version? See [`../spring-boot-4-actuator`](../spring-boot-4-actuator).
> A side-by-side comparison of the two is in the [parent README](../README.md).

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
| `info` | Build, git, java, os + custom contributor | `curl localhost:8080/actuator/info` |
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

Tracing is configured to export OTLP to Tempo at `http://localhost:4318/v1/traces`
with 100% sampling, and every log line is prefixed with `traceId`/`spanId` so logs
and traces correlate.

---

## Tests

[`ActuatorEndpointsIntegrationTest`](src/test/java/com/example/actuator/ActuatorEndpointsIntegrationTest.java)
spins up a real Postgres with **Testcontainers** and asserts that a representative
slice of the actuator surface responds (public health, probes, the custom
endpoint, Prometheus, and that secured endpoints reject anonymous access). Requires
a running Docker daemon.

```bash
./gradlew test
```
