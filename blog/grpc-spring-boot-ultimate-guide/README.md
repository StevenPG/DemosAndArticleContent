# Ultimate Guide to gRPC in Spring Boot 4.1 — Companion Project

A complete, heavily-commented, two-service gRPC system built on **Spring Boot 4.1**
and **Spring gRPC 1.0** (`org.springframework.grpc`). It demonstrates every major
gRPC feature you'd use in production — all four RPC method types, shared proto
contracts with build-time code generation, interceptors on both sides, exception
handling, deadlines, health, reflection, and in-process testing — across two
services you can run side by side and test against each other.

```
                 REST / JSON                    gRPC / protobuf (HTTP/2)
  curl  ────────────────────►  storefront-client ────────────────────►  inventory-server
         localhost:8080              (client)        localhost:9090         (server)
                                        │                                      │
                                        └──────────► inventory-proto ◄─────────┘
                                              the shared, compiled contract
```

| Module | What it is |
|---|---|
| `inventory-proto` | The `.proto` contract + Java code generated from it at build time. No hand-written code. |
| `inventory-server` | A **pure gRPC** Spring Boot service (no Tomcat, no HTTP) on port 9090. |
| `storefront-client` | A Spring MVC app on port 8080 that consumes the gRPC API and re-exposes it as REST/JSON — the classic "gRPC inside, JSON at the edge" topology. |

---

## Quick start

```bash
./scripts/run-demo.sh        # builds everything, starts both services
./scripts/demo-requests.sh   # exercises all four RPC types end to end
./scripts/grpcurl-examples.sh# talk gRPC directly (optional, needs grpcurl)
./scripts/stop-demo.sh       # tears it all down
```

Or run pieces manually:

```bash
./gradlew build                          # compile + codegen + all tests
./gradlew :inventory-server:bootRun      # terminal 1
./gradlew :storefront-client:bootRun     # terminal 2
curl localhost:8080/api/products | jq
```

Requirements: Java 21+ (Gradle downloads itself and protoc; nothing else to install).

---

## gRPC in five minutes

**What it is.** gRPC is a high-performance RPC framework: you define services
and messages once in a `.proto` file, a compiler generates client and server
code in your language(s), and calls travel as compact binary **protobuf**
messages over **HTTP/2**.

**Why teams pick it over REST for service-to-service traffic:**

- **A compiled contract.** The `.proto` file is the API. Server and client are
  generated from the same source, so drift between "the docs" and "the code"
  is impossible. Breaking changes surface at compile time, not at 3 a.m.
- **Performance.** Protobuf is a compact binary encoding (field numbers, not
  field names, go on the wire) and HTTP/2 multiplexes many concurrent calls
  over one TCP connection with header compression.
- **Streaming is native.** Server push, client upload, and full bidirectional
  streams are first-class method types — no WebSocket bolt-ons, no polling.
- **Polyglot.** One `.proto` generates idiomatic code for Java, Go, Python,
  TypeScript, Rust, Swift... ideal for heterogeneous microservice fleets.
- **Rich ecosystem semantics.** Deadlines, cancellation, retries, load
  balancing, health checking, and auth are part of the framework, not
  conventions you reinvent.

**Where REST still wins:** browsers (gRPC needs a proxy like grpc-web),
human-debuggable payloads, public APIs where curl-ability matters. Hence this
project's topology — gRPC between services, JSON at the edge.

### The four method types

The entire gRPC feature space for methods is four shapes, defined by whether
each side sends one message or a stream:

| # | Type | Signature in `.proto` | This project's example |
|---|---|---|---|
| 1 | Unary | `rpc Get(Req) returns (Resp)` | `GetProduct`, `ListProducts` |
| 2 | Server streaming | `rpc Watch(Req) returns (stream Resp)` | `WatchStock` — live stock feed |
| 3 | Client streaming | `rpc Upload(stream Req) returns (Resp)` | `RecordShipments` — batch ingestion |
| 4 | Bidirectional | `rpc Chat(stream Req) returns (stream Resp)` | `ProcessOrders` — pipelined order processing |

All four are defined in
[`inventory-proto/src/main/proto/inventory/v1/inventory.proto`](inventory-proto/src/main/proto/inventory/v1/inventory.proto),
implemented in
[`InventoryGrpcService`](inventory-server/src/main/java/com/stevenpg/grpc/inventory/grpc/InventoryGrpcService.java),
and consumed in
[`StorefrontController`](storefront-client/src/main/java/com/stevenpg/grpc/storefront/api/StorefrontController.java).
Each file explains its side of the story in comments.

---

## Spring Boot + gRPC: what Spring gRPC gives you

Historically, gRPC in Spring Boot meant third-party starters
(`grpc-spring-boot-starter` from yidongnan/grpc-ecosystem, or LogNet's). Since
2024 there is an **official Spring project** — [Spring gRPC](https://docs.spring.io/spring-grpc/reference/)
(`org.springframework.grpc`, 1.0 GA) — and it is what Spring Boot 4.x users
should reach for. What it autoconfigures:

**Server side** (`spring-grpc-spring-boot-starter`):

- Boots a Netty gRPC server (default port 9090; servlet-embedded mode exists
  too if you want gRPC and MVC sharing one port).
- **Auto-registers every `BindableService` bean.** Your service is just
  `@Service class X extends FooGrpc.FooImplBase` — no manual server wiring.
- Applies `@GlobalServerInterceptor` beans to every service.
- Routes exceptions through `GrpcExceptionHandler` beans (the gRPC analog of
  `@RestControllerAdvice`).
- Registers the standard **reflection** and **health** services when
  `io.grpc:grpc-services` is present.
- Everything under `spring.grpc.server.*`: port, TLS via SSL bundles,
  keep-alive, message size limits, graceful shutdown.

**Client side** (`spring-grpc-client-spring-boot-starter`):

- **Named channels from configuration** — `spring.grpc.client.channels.<name>.*`
  holds the address, TLS, and keep-alive settings; code asks
  `GrpcChannelFactory` for the channel by name. Environments override config,
  not code.
- Applies `@GlobalClientInterceptor` beans to every channel.
- Optional `@ImportGrpcClients` to register generated stubs as beans
  automatically (this project builds them explicitly in `GrpcClientConfig`
  so you can see the moving parts).

The BOM `org.springframework.grpc:spring-grpc-dependencies` pins matching
versions of `io.grpc:*` and `protobuf-java` — use it and never chase version
skew between codegen and runtime.

---

## The build-time code generation pipeline

`inventory-proto` is where the "auto-generated stuff" happens. The
`com.google.protobuf` Gradle plugin wires this chain into every build:

```
inventory.proto ──► protoc ──────────────► Product.java, StockUpdate.java, ...   (messages)
                      │
                      └─ protoc-gen-grpc-java ──► InventoryServiceGrpc.java      (service)
                                                    ├─ InventoryServiceImplBase  ← server extends
                                                    ├─ newBlockingStub()         ← sync client
                                                    ├─ newStub()                 ← async/streaming client
                                                    └─ newFutureStub()           ← ListenableFuture client
```

Key points worth featuring:

- Generated code lands in `build/generated/` and is **never committed** —
  the `.proto` is the only source of truth.
- Gradle downloads `protoc` and the codegen plugin from Maven Central, so
  contributors install nothing.
- Both service modules just declare `implementation(project(":inventory-proto"))`;
  in a real org this module is its own repo or a published artifact so Go and
  Python services can generate from the same file.

---

## Feature tour (a map for the article)

| Feature | Where to look |
|---|---|
| Proto design: field numbering, enums with UNSPECIFIED, `oneof`, `map`, well-known `Timestamp`, `v1` package versioning | `inventory-proto/.../inventory.proto` |
| All four RPC types, server side (incl. cancellation checks on streams) | `InventoryGrpcService` |
| All four RPC types, client side (blocking vs async stubs) | `StorefrontController` |
| Exception → gRPC status mapping (`GrpcExceptionHandler` beans) | `GrpcExceptionConfig` (server) |
| gRPC status → HTTP status mapping at the REST edge | `GrpcStatusRestAdvice` (client) |
| Server interceptor: per-call logging, timing, reading metadata | `GrpcInterceptorConfig` |
| Client interceptor: attaching metadata (auth-token pattern) | `GrpcClientInterceptorConfig` |
| **Deadlines** (`withDeadlineAfter`) and why they matter | `StorefrontController.getProduct` |
| Server streaming → **Server-Sent Events** bridge | `StorefrontController.streamStock` |
| Channel configuration: `static://` addresses, plaintext vs TLS, keep-alive | `storefront-client/.../application.yml` |
| Server configuration: port, reflection, health, graceful shutdown | `inventory-server/.../application.yml` |
| **In-process transport testing** (`@AutoConfigureInProcessTransport`) | `InventoryGrpcServiceIntegrationTest` |
| Reflection service + grpcurl workflow | `scripts/grpcurl-examples.sh` |
| Standard health service (`grpc.health.v1.Health`, k8s probes) | `application.yml` + grpcurl script |
| Domain model vs wire model separation | `ProductRecord` + mapping helpers |

### Things the demo makes visible

- **Streaming is real**: the SSE endpoint (`/api/products/{sku}/stock/stream`)
  emits events ~400 ms apart — you watch messages arrive rather than getting a
  buffered list. Same for `grpcurl ... WatchStock`.
- **Bidirectional interleaving**: run the orders demo and read
  `.demo/storefront-client.log` — `<- order-001 : CONFIRMED` responses arrive
  between the `-> order-00X` sends.
- **Interceptors on both ends**: the client stamps `x-client-id` metadata on
  every call; the server's logging interceptor reads it and logs
  `gRPC inventory.v1.InventoryService/GetProduct from client [storefront-client] -> OK in 3 ms`.
- **Error semantics survive the whole chain**: unknown SKU →
  `ProductNotFoundException` → gRPC `NOT_FOUND` → HTTP `404` with a JSON body.

---

## Testing story

- **`inventory-server`** runs full-stack integration tests over gRPC's
  **in-process transport** (`@AutoConfigureInProcessTransport` from
  `spring-grpc-test`): real marshalling, real interceptors, real exception
  handlers — no network port. All four RPC types are covered, including the
  async streaming calls.
- **`storefront-client`** verifies the Spring context: channel properties
  parse and stub beans construct (channels connect lazily, so no server is
  needed).
- **End-to-end** is covered by the shell scripts against the live pair of
  services.

```bash
./gradlew test          # everything
./gradlew :inventory-server:test --tests '*IntegrationTest'
```

---

## Cheat sheet: gRPC status codes vs HTTP

The mapping implemented in `GrpcStatusRestAdvice`:

| gRPC status | Typical meaning | HTTP |
|---|---|---|
| `OK` | success | 200 |
| `NOT_FOUND` | entity doesn't exist | 404 |
| `INVALID_ARGUMENT` | bad request data | 400 |
| `ALREADY_EXISTS` / `ABORTED` | conflict | 409 |
| `PERMISSION_DENIED` | authz failure | 403 |
| `UNAUTHENTICATED` | authn failure | 401 |
| `RESOURCE_EXHAUSTED` | rate limited / quota | 429 |
| `FAILED_PRECONDITION` | state doesn't allow it | 412 |
| `UNIMPLEMENTED` | method not offered | 501 |
| `UNAVAILABLE` | transient, retryable | 503 |
| `DEADLINE_EXCEEDED` | call took too long | 504 |
| `UNKNOWN` | unmapped server exception | 500 |

If clients see `UNKNOWN`, your server is missing an exception handler — see
`GrpcExceptionConfig` for the fix.

---

## Production notes (beyond the demo)

- **Always set deadlines** on clients; they propagate downstream and prevent
  thread pile-ups. (`withDeadlineAfter`, or per-channel `default-deadline`.)
- **One channel per target service**, shared for the app's lifetime. Stubs are
  free; channels are not.
- **TLS**: flip `negotiation-type: tls` and point `ssl.bundle` at a Spring SSL
  bundle; the server side is symmetric under `spring.grpc.server.ssl.*`.
- **Health + load balancing**: the standard health service is what
  Kubernetes' native gRPC probes and `grpc_health_probe` consume.
- **Reflection** is invaluable in dev (grpcurl/Postman) — decide deliberately
  whether to leave it on in production.
- **Streams need hygiene**: check `Context.current().isCancelled()` in
  long-running server streams, and always correlate messages with IDs on
  bidirectional streams.
- **Observability**: Spring gRPC integrates with Micrometer out of the box
  (`spring.grpc.server.observation.enabled`, on by default when Micrometer is
  present).

## Version matrix

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring gRPC | 1.0.3 (`spring-grpc-dependencies` BOM) |
| grpc-java | 1.77.1 (via BOM) |
| protobuf | 4.33.4 (via BOM) |
| protobuf Gradle plugin | 0.9.5 |
