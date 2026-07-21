# The Ultimate Guide to Spring Cloud Gateway on Spring Boot 4 — Companion Project

A single, runnable system that packs **every major Spring Cloud Gateway feature**
into one place — built **twice**, once on each of the two flavors the gateway ships
on Spring Boot 4:

- **`gateway-webflux`** — the reactive gateway (Netty, non-blocking).
- **`gateway-webmvc`** — the servlet gateway (Tomcat, blocking, functional routes).

Both gateways front the **same two backend services** and demonstrate the same
feature set, so you can read the reactive and servlet versions of each idea
side by side and see exactly what changes (usually: very little).

> Version matrix — everything here is built and tested against:
>
> | Piece | Version |
> |---|---|
> | Spring Boot | **4.0.7** |
> | Spring Cloud | **2025.1.2** ("Oakwood") |
> | Spring Cloud Gateway | **5.0.2** |
> | Java | **21** |
> | Gradle | **8.14** |
> | Redis | 7 (via Docker) |

---

## The system

```
                                          ┌──────────────────────────────┐
                                          │  backend-orders     :8081     │
                    ┌── http ────────────►│  (orders, echo, flaky, slow)  │
                    │                     └──────────────────────────────┘
  curl              │
   │   ┌────────────┴───────────┐         ┌──────────────────────────────┐
   ├──►│ gateway-webflux  :8080 │── lb ──►│  backend-inventory  :8082     │
   │   │ (reactive / Netty)     │    │    │  backend-inventory  :8083     │
   │   └────────────────────────┘    │    │  (two instances, round-robin) │
   │                                 │    └──────────────────────────────┘
   │   ┌────────────────────────┐    │
   └──►│ gateway-webmvc   :8090 │── lb ┘         ┌───────────────┐
       │ (servlet / Tomcat)     │───────────────►│  Redis  :6379 │  (rate limiting)
       └────────────────────────┘                └───────────────┘
```

| Module | What it is |
|---|---|
| `backend-orders` | A plain Spring MVC service (port 8081). Has endpoints designed to trigger gateway features: `/orders/echo` reflects headers, `/orders/flaky` fails 2 of 3 calls, `/orders/slow` sleeps. It has **no idea** a gateway exists. |
| `backend-inventory` | A plain Spring MVC service. We run **two instances** (8082, 8083), each reporting which one it is, to demonstrate load balancing. |
| `gateway-webflux` | The **reactive** gateway (8080). Routes in `application.yml` + a programmatic `RouteLocator`. |
| `gateway-webmvc` | The **servlet** gateway (8090). Routes as functional `RouterFunction` beans + one declarative YAML route. |

---

## Quick start

Requirements: **Java 21+** and **Docker** (for Redis). Gradle is provided by the wrapper.

```bash
./scripts/run-demo.sh        # builds everything, starts Redis + backends + both gateways
./scripts/demo-requests.sh   # exercises EVERY feature against both gateways, labeled
./scripts/stop-demo.sh       # tears it all down
```

> **Note on Java versions:** the project's Java *toolchain* (what compiles/runs the
> app) is 21, but the Gradle *wrapper itself* (8.14) can't run on JDK 24+ as its host
> JVM — it fails compiling `build.gradle.kts` with a cryptic `IllegalArgumentException`
> whose message is just the JDK's major version number. `run-demo.sh` auto-detects
> this and switches to an installed 17-23 JDK for you. If you invoke `./gradlew`
> directly and your default `java` is 24+, do the same yourself first, e.g.
> `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (macOS) or `sdk use java 21.0.3-tem`
> (SDKMAN).

Or drive it by hand (reactive gateway shown; use `:8090` for the servlet one):

```bash
# public route, load-balanced across the two inventory instances
curl -s localhost:8080/inventory/whoami        # -> {"instance":"inventory:8082"} then :8083 ...

# protected route without a token -> 401
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/orders    # -> 401

# mint a demo token (dev profile only) and use it
TOKEN=$(curl -s 'localhost:8080/dev/token?sub=alice' | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/orders | jq
```

---

## The big decision: reactive (WebFlux) vs servlet (WebMVC)

On Spring Boot 4, Spring Cloud Gateway comes in two independent flavors. They are
**separate starters** and you pick one per gateway app:

```kotlin
// reactive: Netty, non-blocking, YAML/RouteLocator routes
implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

// servlet: Tomcat, blocking, functional RouterFunction routes
implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
```

| | **WebFlux** (`gateway-webflux`) | **WebMVC** (`gateway-webmvc`) |
|---|---|---|
| Runtime | Netty, event loop | Tomcat, thread-per-request |
| Model | Non-blocking / reactive | Blocking |
| Route definition | YAML `routes:` and/or `RouteLocator` DSL | Functional `RouterFunction` beans and/or YAML |
| Rate limiter | Built-in `RequestRateLimiter` (Redis Lua token bucket) | `Bucket4jFilterFunctions.rateLimit` (Bucket4j; Redis via bucket4j-redis) |
| Circuit breaker | `spring-cloud-starter-circuitbreaker-**reactor**-resilience4j` | `spring-cloud-starter-circuitbreaker-resilience4j` |
| Best when | High connection counts, streaming, an already-reactive stack | A blocking/servlet mental model, or you want functional routes and easy debugging |

**Which should you pick?** If you have no strong reason to go reactive, the servlet
gateway is the simpler mental model and debugger experience. Choose WebFlux when you
expect very high concurrency, do streaming/SSE proxying, or already run a reactive
stack. This project builds both so you never have to guess what the other side
looks like.

---

## Feature tour

Every feature below is live in **both** gateways. Each section shows the reactive
config, the servlet config, and how to *see* it with curl.

### 1. Routing, predicates & path rewriting

A route is *predicate(s)* → *filters* → *uri*. Predicates decide **if** a request
matches (path, method, header, host, query, time, weight…); filters transform the
request/response on the way through.

**Reactive** — declarative in [`gateway-webflux/.../application.yml`](gateway-webflux/src/main/resources/application.yml):

```yaml
- id: inventory
  uri: lb://backend-inventory
  predicates:
    - Path=/inventory/**
    - Method=GET
  filters:
    - AddResponseHeader=X-Load-Balanced, "true"
```

**Servlet** — functional in [`gateway-webmvc/.../RoutesConfig.java`](gateway-webmvc/src/main/java/com/stevenpg/gateway/webmvc/config/RoutesConfig.java):

```java
route("inventory")
    .route(path("/inventory/**").and(method(HttpMethod.GET)), http())
    .filter(lb("backend-inventory"))
    .after(addResponseHeader("X-Load-Balanced", "true"))
    .build();
```

Path rewriting is shown on the "alternate prefix" routes: the reactive gateway's
programmatic route exposes orders under `/java/orders/**` and rewrites back to
`/orders/**`; the servlet gateway's YAML route does the same under `/yaml/orders/**`.

### 2. Filters: built-in, a custom GlobalFilter, and a custom filter factory

Three levels of filter, all present here:

- **Built-in filters** — `AddRequestHeader`, `RemoveRequestHeader`,
  `AddResponseHeader`, `RewritePath`, `DedupeResponseHeader`, … configured per route.
- **A custom GlobalFilter** — runs on *every* route. See
  [`GlobalLoggingFilter`](gateway-webflux/src/main/java/com/stevenpg/gateway/webflux/filter/GlobalLoggingFilter.java)
  (reactive `GlobalFilter`) and its servlet twin (a `OncePerRequestFilter`). Both log
  `--> METHOD /path` / `<-- METHOD /path STATUS (Nms)` and stamp `X-Gateway-Handled`.
- **A custom, configurable filter factory** — see
  [`AddCorrelationIdGatewayFilterFactory`](gateway-webflux/src/main/java/com/stevenpg/gateway/webflux/filter/AddCorrelationIdGatewayFilterFactory.java).
  Spring derives its YAML name (`AddCorrelationId`) from the class name. It mints or
  preserves an `X-Correlation-Id`, forwards it to the backend, and echoes it on the
  response. The servlet gateway implements the identical behavior as a `before`/`after`
  filter pair.

See it — the echo endpoint reflects exactly what the backend received:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/orders/echo | jq .receivedHeaders
# {
#   "X-Gateway": "webflux",
#   "X-Request-Start": "gateway",
#   "X-Correlation-Id": "d5bf9846-...",   <- our custom filter
#   "X-Auth-Subject": "alice",            <- see §5
#   ...
# }
```

### 3. Load balancing

Both gateways route `backend-inventory` traffic across the two instances with a
`lb://` route. No Eureka/Consul required — a static instance list under
`spring.cloud.discovery.client.simple.instances` feeds Spring Cloud LoadBalancer:

```yaml
spring:
  cloud:
    discovery:
      client:
        simple:
          instances:
            backend-inventory:
              - uri: http://localhost:8082
              - uri: http://localhost:8083
```

See it — the answering instance round-robins:

```bash
for i in 1 2 3 4; do curl -s localhost:8080/inventory/whoami; echo; done
# {"instance":"inventory:8082"}
# {"instance":"inventory:8083"}
# {"instance":"inventory:8082"}
# {"instance":"inventory:8083"}
```

### 4. Resilience: retry, circuit breaker + fallback, rate limiting

**Retry** — `/orders/flaky` fails 2 of every 3 calls with `503`. A Retry filter
re-issues the request so the caller almost always sees `200`. (Hit the backend
directly on `:8081` to watch it actually fail.)

**Circuit breaker + fallback** — the orders route is wrapped in a Resilience4j
CircuitBreaker with a **1s time limiter**. `/orders/slow` sleeps 3s, so the gateway
gives up and does an internal `forward:/fallback/orders`:

```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/orders/slow
# {"service":"orders","message":"Orders is unavailable ... served by the gateway fallback.", ...}   (HTTP 503, ~1s)
```

The breaker also **opens on failure rate** (≥50% of a 10-call window) and then
short-circuits every call to the fallback until it half-opens again.

**Rate limiting** — this is the most interesting divergence between the flavors:

- **Reactive** uses the built-in `RequestRateLimiter` filter, a Redis Lua **token
  bucket** (`replenishRate` 5/s, `burstCapacity` 10), keyed by a `KeyResolver` bean
  (authenticated subject, falling back to client IP).
- **Servlet** uses `Bucket4jFilterFunctions.rateLimit(...)`, backed by **Bucket4j**.
  For a *distributed* limit we store the buckets in Redis via `bucket4j-redis`
  (`LettuceBasedProxyManager` → `AsyncProxyManager<String>`). Same idea, different
  engine — see
  [`RateLimitConfig`](gateway-webmvc/src/main/java/com/stevenpg/gateway/webmvc/ratelimit/RateLimitConfig.java).

See it — a burst gets throttled once the bucket empties:

```bash
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code} " -H "Authorization: Bearer $TOKEN" localhost:8080/orders/o-1001
done; echo
# 200 200 200 200 200 200 200 200 200 200 429 429 429 429 429 429 429 429 429 429
```

Both return the standard `X-RateLimit-Remaining` / `X-RateLimit-Burst-Capacity`
headers.

### 5. Security at the edge

The gateway is an **OAuth2 resource server**: it validates the Bearer JWT once, at
the edge, so the backends can trust that anything reaching them is already
authenticated. Policy (identical in both flavors, see the two `SecurityConfig`s):

- `/orders/**` → authenticated (valid JWT required)
- `/inventory/**`, `/actuator/health`, `/fallback/**`, `/dev/**` → public

Two things worth calling out:

- **Identity propagation** — after validating the token, a global filter forwards the
  subject downstream as `X-Auth-Subject`, so the backend never touches a token. It
  **strips any client-supplied `X-Auth-Subject` first** — otherwise a caller could
  forge the header and impersonate anyone (the classic gateway "confused deputy").
  Try it: send `-H "X-Auth-Subject: HACKER"` with a token for `alice` and the backend
  still sees `alice`.
- **Self-contained tokens** — the demo validates HS256 tokens with a shared secret so
  it runs with no external IdP, and a **dev-only** `/dev/token` endpoint mints them.
  In production you delete both and point
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` at your real IdP; the
  gateway then only ever *validates*.

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/orders                       # 401
TOKEN=$(curl -s 'localhost:8080/dev/token?sub=alice' | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" localhost:8080/orders   # 200
```

### 6. Observability

Three layers, from most to least "batteries included":

1. **Actuator** — `/actuator/gateway/routes` lists every live route,
   `/actuator/metrics` exposes Micrometer meters (`http.server.requests`, …),
   `/actuator/circuitbreakers` shows Resilience4j state.
   ```bash
   curl -s localhost:8080/actuator/gateway/routes | jq '.[].route_id'
   # "orders-java" "orders-flaky" "orders" "inventory"
   ```
   Note: `/actuator/gateway/*` is **reactive-only** — as of Spring Cloud Gateway
   5.0.2, the servlet/functional flavor (`gateway-webmvc`, port 8090) doesn't
   register those endpoints, so the same call there 404s. `/actuator/mappings`,
   `/actuator/metrics`, and `/actuator/circuitbreakers` all work on both.
2. **Correlation id** — the custom `AddCorrelationId` filter (see §2) gives you
   always-on, exporter-free request correlation across the gateway and every backend
   it calls.
3. **Distributed tracing** — Micrometer Tracing + Brave is on the classpath and
   sampling is 100%, so each request produces a span you can export to Zipkin or an
   OTLP collector. (Point it at a collector to see full traces; the correlation id
   covers you until you do.)

---

## Declarative vs programmatic routing (both flavors support both)

A deliberate symmetry in this project:

- **`gateway-webflux`** defines most routes in **YAML** and adds one **programmatic**
  route via a `RouteLocator` bean (`/java/orders/**`).
- **`gateway-webmvc`** defines most routes with the **functional** `RouterFunction`
  API and adds one **declarative** route in YAML (`/yaml/orders/**`).

So between the two modules you get all four combinations, and can decide per-team
which style you prefer. YAML is compact and hot-reloadable; the programmatic/functional
styles give you compile-time checking, IDE navigation, and access to other beans.

---

## Project layout

```
spring-cloud-gateway-ultimate-guide/
├── settings.gradle.kts            # 4 modules
├── build.gradle.kts               # shared config: Boot + Spring Cloud BOM, Java 21
├── docker-compose.yml             # Redis
├── scripts/                       # run-demo / demo-requests / stop-demo
├── backend-orders/                # downstream service (routing/filters/resilience/security target)
├── backend-inventory/             # downstream service (load-balancing target, 2 instances)
├── gateway-webflux/               # reactive gateway
│   └── src/main/java/.../webflux/
│       ├── config/RoutesConfig.java            # programmatic RouteLocator
│       ├── filter/GlobalLoggingFilter.java     # custom GlobalFilter
│       ├── filter/AddCorrelationIdGatewayFilterFactory.java  # custom filter factory
│       ├── ratelimit/RateLimiterConfig.java    # KeyResolver
│       ├── security/SecurityConfig.java        # edge JWT validation
│       ├── security/IdentityPropagationGlobalFilter.java     # forward identity, anti-spoof
│       ├── web/FallbackController.java          # circuit-breaker fallback
│       └── dev/DevTokenController.java          # demo token minter (dev profile)
└── gateway-webmvc/                # servlet gateway — same components, servlet flavor
    └── src/main/java/.../webmvc/
        ├── config/RoutesConfig.java            # functional RouterFunction beans
        ├── ratelimit/RateLimitConfig.java      # Bucket4j + Redis proxy manager
        └── ... (security, filter, web, dev — mirrors of the reactive side)
```

---

## Building & testing

```bash
./gradlew assemble          # build every module's jar (no Docker needed)
./gradlew test              # run tests
```

Tests come in two kinds:

- **Unit tests** (no Docker) — e.g.
  `AddCorrelationIdGatewayFilterFactoryTest` (reactive) and `RateLimitConfigTest`
  (servlet) verify the custom filter/key-resolver logic in isolation.
- **Integration tests** (need Docker) — `GatewayWebfluxIntegrationTest` and
  `GatewayWebmvcIntegrationTest` boot the full gateway against a **real Redis** via
  Testcontainers and assert security, route registration, and JWT validation
  end to end. The reactive test uses `WebTestClient`; the servlet test uses Spring's
  new `RestTestClient` — otherwise they are near-identical.

---

## Taking this to production

This is a demo; a few things are deliberately simplified. Before shipping:

- **Replace the shared HS256 secret** and delete `DevTokenController`. Point the
  resource server at your IdP's JWKS (`jwk-set-uri`). Consider whether to keep
  forwarding the raw `Authorization` header downstream or only the derived identity.
- **Externalize the discovery list** — swap the static instance list for real service
  discovery (Kubernetes, Eureka, Consul) so instances come and go automatically.
- **Tune Resilience4j** (window sizes, thresholds, timeouts) per route and per SLA.
- **Wire tracing to a collector** (Zipkin/OTLP) and scrape `/actuator/prometheus`.
- **Run Redis in HA** (Sentinel/Cluster) — it's on the request path for rate limiting.

---

## Further reading

- Spring Cloud Gateway reference: https://docs.spring.io/spring-cloud-gateway/reference/
- Spring Cloud 2025.1.0 ("Oakwood") release notes: https://spring.io/blog/2025/11/25/spring-cloud-2025-1-0-aka-oakwood-has-been-released/
- Resilience4j: https://resilience4j.readme.io/
- Related demo in this repo: `../bucket4j-redis-rate-limiting` (distributed rate limiting deep-dive)
