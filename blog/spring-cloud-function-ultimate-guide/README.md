# The Ultimate Guide to Spring Cloud Function — Companion Project

One set of business functions, written **once** as plain `java.util.function`
beans, exposed over **four different surfaces** without changing a line of the
functions: **HTTP**, **Kafka**, **RSocket**, and **AWS Lambda**.

That is the whole pitch of Spring Cloud Function (SCF): you write functions, not
controllers or listeners or handlers. SCF's `FunctionCatalog` discovers them,
composes them, converts their payloads, routes between them — and a thin
*adapter* per surface does the wiring. This project makes that concrete.

> Version matrix — everything here is built and tested against:
>
> | Piece | Version |
> |---|---|
> | Spring Boot | **4.0.7** |
> | Spring Cloud | **2025.1.2** ("Oakwood") |
> | Spring Cloud Function | **5.0.3** (pinned by the BOM) |
> | Spring Cloud Stream | **5.0.2** (pinned by the BOM) |
> | Java | **21** |
> | Gradle | **8.14** |
> | Kafka | apache/kafka **3.9** (via Docker / Testcontainers) |

---

## The one idea

```
                         ┌──────────────────────────────────────────────┐
                         │              functions-core                   │
                         │  (plain java.util.function beans — NO server) │
                         │                                               │
                         │  generateOrders : Supplier<Order>             │
                         │  enrichOrder    : Function<Order,EnrichedOrder>│
                         │  validateOrder  : Function<EnrichedOrder,Dec.> │
                         │  notify         : Consumer<Decision>          │
                         │  + reactive / tuple / message / routing /     │
                         │    csv-converter / dynamic variants           │
                         └───────────────────────┬──────────────────────┘
                                                 │  discovered via FunctionCatalog
        ┌───────────────────┬───────────────────┼───────────────────┬───────────────────┐
        ▼                   ▼                   ▼                   ▼                   
  ┌───────────┐      ┌──────────────┐     ┌────────────┐     ┌───────────────┐
  │  app-web  │      │app-stream-   │     │ app-rsocket│     │  adapter-aws  │
  │  HTTP     │      │kafka         │     │ RSocket    │     │  Lambda       │
  │  :8080    │      │Kafka topics  │     │ :7000 TCP  │     │  (local-only) │
  └───────────┘      └──────────────┘     └────────────┘     └───────────────┘
```

Every module below `functions-core` writes **zero business logic**. Read any two
side by side and notice the functions are identical; only the exposure differs.

| Module | Surface | What it adds on top of the functions |
|---|---|---|
| `functions-core` | — | The functions + POJOs + a custom converter + a runtime-registered function. The only module with business logic. |
| `app-web` | HTTP | `spring-cloud-function-web` maps every function to `POST /<name>` — no controllers. |
| `app-stream-kafka` | Kafka | Spring Cloud Stream binds a function to `orders → decisions` topics; adds a **dead-letter queue** and **tracing**. |
| `app-rsocket` | RSocket | The "no turnkey adapter" pattern: inject the `FunctionCatalog`, invoke by name over `@MessageMapping`. |
| `adapter-aws` | AWS Lambda | `spring-cloud-function-adapter-aws`'s `FunctionInvoker` as the Lambda handler. Demonstrated **locally**. |

---

## Quick start

Requirements: **Java 21+** and **Docker** (for Kafka). Gradle is provided by the
wrapper.

```bash
./scripts/run-demo.sh        # builds everything, starts Kafka + all three apps
./scripts/demo-requests.sh   # exercises every feature, labeled
./scripts/stop-demo.sh       # tears it all down
```

> **Note on Java versions:** the project's Java *toolchain* is 21, but the Gradle
> *wrapper* (8.14) cannot run on JDK 24+ as its host JVM. `run-demo.sh`
> auto-detects this and switches to an installed 17–23 JDK for you. If you invoke
> `./gradlew` directly and your default `java` is 24+, do the same first, e.g.
> `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (macOS) or
> `sdk use java 21.0.3-tem` (SDKMAN).

Or run just the tests (no Docker needed for most — the Kafka test skips itself
when Docker is absent):

```bash
./gradlew test
```

---

## The concepts, one at a time

### 1. Function / Supplier / Consumer — the programming model

The three shapes of a unit of work, all from `java.util.function`:

```java
@Bean public Supplier<Order> generateOrders() { ... }              // a source
@Bean public Function<Order, EnrichedOrder> enrichOrder() { ... }  // a transform
@Bean public Consumer<Decision> notify() { ... }                   // a sink
```

Nothing here imports anything web, Kafka, or AWS. See
[`PipelineFunctions.java`](functions-core/src/main/java/com/stevenpg/scf/PipelineFunctions.java).

Over HTTP:

```bash
curl localhost:8080/generateOrders                                  # the Supplier
curl -XPOST localhost:8080/enrichOrder -H 'Content-Type: application/json' \
     -d '{"orderId":"ord-1","customerId":"cust-alice","amount":199.99,"currency":"USD","itemCount":2}'
```

### 2. Composition

Chain functions with `|` in the function definition — the catalog builds the
composite for you:

```bash
# enrichOrder|validateOrder is a Function<Order, Decision>
curl -XPOST localhost:8080/enrichOrder,validateOrder \
     -H 'Content-Type: application/json' -d '{...order...}'
```

Over HTTP the delimiter in the path is a comma; everywhere else (YAML,
`FunctionCatalog.lookup`) it is the pipe `|`. Compose a `Consumer` onto the end
(`enrichOrder|validateOrder|notify`) and you get a `Consumer<Order>`.

### 3. Routing

Dispatch one input to different functions at runtime via the built-in
`functionRouter`. Precedence: a `spring.cloud.function.definition` **header**, then
a `routing-expression` (SpEL), then a `MessageRoutingCallback` bean. This project
uses the callback in
[`RoutingConfig.java`](functions-core/src/main/java/com/stevenpg/scf/RoutingConfig.java):

```bash
# small "express" orders take the cheap fastApprove path...
curl -XPOST localhost:8080/functionRouter -H 'order-channel: express' \
     -H 'Content-Type: application/json' -d '{...order...}'
# ...everything else runs the full enrichOrder|validateOrder pipeline
curl -XPOST localhost:8080/functionRouter \
     -H 'Content-Type: application/json' -d '{...order...}'
```

### 4. `Message<T>`, headers & POJO conversion

Declare the input/output as `Message<T>` to get the headers alongside the
converted payload.
[`decideWithHeaders`](functions-core/src/main/java/com/stevenpg/scf/MessageFunctions.java)
echoes a `channel` header and adds derived ones — over HTTP they become response
headers, over Kafka they become record headers.

```bash
curl -i -XPOST localhost:8080/decideWithHeaders -H 'channel: mobile-app' \
     -H 'Content-Type: application/json' -d '{...order...}'
# response carries: channel, decision-outcome, customer-tier, processed-by
```

### 5. Custom content types

A `MessageConverter` bean adds a wire format the SAME functions can be called
with. [`CsvOrderMessageConverter`](functions-core/src/main/java/com/stevenpg/scf/CsvOrderMessageConverter.java)
teaches the pipeline `text/csv`:

```bash
curl -XPOST localhost:8080/enrichOrder -H 'Content-Type: text/csv' \
     -d 'ord-9,cust-alice,199.99,USD,3'
```

### 6. Reactive variants

Functions can operate on the stream itself with `Flux`/`Mono`. SCF treats
imperative and reactive uniformly, so an adapter can call either. See
[`ReactiveFunctions.java`](functions-core/src/main/java/com/stevenpg/scf/ReactiveFunctions.java)
— including a reactive `Supplier<Flux<Order>>` that is an unbounded source.

### 7. Multi-arity (tuples)

More than one input or output, using Reactor's `Tuple2` of `Flux`. On a binder
each tuple slot is its own topic — fan-in and fan-out. See
[`TupleFunctions.java`](functions-core/src/main/java/com/stevenpg/scf/TupleFunctions.java).

### 8. Dynamic (runtime) registration

The `FunctionCatalog` is also a `FunctionRegistry`: push new functions in while
running. [`DynamicFunctionRegistrar`](functions-core/src/main/java/com/stevenpg/scf/DynamicFunctionRegistrar.java)
registers `dynamicUppercase` at startup, and it becomes callable on every
surface:

```bash
curl -XPOST localhost:8080/dynamicUppercase -H 'Content-Type: text/plain' -d 'hello'
# -> HELLO
```

---

## The messaging surface (Kafka) in detail

`app-stream-kafka` binds one function, `orderPipeline` (which is just
`enrichOrder.andThen(validateOrder)` — no new logic), to Kafka:

```
orders topic  ──►  orderPipeline  ──►  decisions topic
                        │
                        └─(throws on unsupported currency)─►  orders-dlq topic
```

- **Bindings** live in [`application.yml`](app-stream-kafka/src/main/resources/application.yml)
  as `orderPipeline-in-0` / `orderPipeline-out-0`.
- **Dead-letter queue**: `enable-dlq: true` + `max-attempts: 1` sends poison
  messages (here: any non-USD order) straight to `orders-dlq` instead of blocking
  the partition.
- **Observability**: `micrometer-tracing-bridge-brave` + `sampling.probability: 1.0`
  trace each invocation.

The [`StreamPipelineTest`](app-stream-kafka/src/test/java/com/stevenpg/scf/StreamPipelineTest.java)
spins up a **real broker with Testcontainers** and asserts both the happy path
and the DLQ path. It skips itself (rather than failing) when Docker is absent.

---

## The RSocket surface: rolling your own adapter

Spring Cloud Function once shipped `spring-cloud-function-rsocket`, but it was
**never released for the GA 5.0.x line** (only `5.0.0` milestones exist on Maven
Central). Rather than pin a milestone against GA artifacts, `app-rsocket` shows
the pattern you use whenever a turnkey adapter is missing or you're on a bespoke
transport: **inject the `FunctionCatalog` and invoke functions by name.**

[`FunctionRSocketController`](app-rsocket/src/main/java/com/stevenpg/scf/FunctionRSocketController.java)
is a handful of lines and lines up with RSocket's interaction models:

| Route | Interaction | Function used |
|---|---|---|
| `orders.enrich` | request/response | `enrichOrder` |
| `orders.decide` | request/response | `enrichOrder\|validateOrder` |
| `orders.decideStream` | request/channel | `reactivePipeline` |

Drive it with the [`rsc`](https://github.com/making/rsc) CLI, or see the real
RSocket-client test
[`FunctionRSocketControllerTest`](app-rsocket/src/test/java/com/stevenpg/scf/FunctionRSocketControllerTest.java).

---

## The AWS Lambda surface (local-only)

`adapter-aws` packages the same pipeline as a Lambda. On AWS you set the handler
to `org.springframework.cloud.function.adapter.aws.FunctionInvoker`; it boots the
Spring app and routes each invocation to the function named by
`spring.cloud.function.definition`.

This guide **does not deploy to a real account**. Instead
[`LambdaHandlerTest`](adapter-aws/src/test/java/com/stevenpg/scf/LambdaHandlerTest.java)
drives the **actual `FunctionInvoker` in-process** (input stream → output
stream), which is exactly what Lambda does at runtime — so you can prove the
deployable unit works with zero cloud setup.

**To actually ship it**, you'd:

1. **Shade** the app into a single jar (Lambda can't read Spring Boot's nested
   jar layout), e.g. with the Shadow plugin:

   ```kotlin
   // build.gradle.kts (sketch)
   plugins { id("com.github.johnrengelman.shadow") version "8.1.1" }
   tasks.shadowJar { archiveClassifier.set("aws"); mergeServiceFiles() }
   ```

2. Deploy with a `template.yaml` and test locally with SAM:

   ```yaml
   # template.yaml (sketch)
   Resources:
     OrderFunction:
       Type: AWS::Serverless::Function
       Properties:
         Handler: org.springframework.cloud.function.adapter.aws.FunctionInvoker
         Runtime: java21
         MemorySize: 512
         Environment:
           Variables:
             SPRING_CLOUD_FUNCTION_DEFINITION: "enrichOrder|validateOrder"
   ```

   ```bash
   sam local invoke OrderFunction -e event.json
   ```

---

## GraalVM native image (documented, not built)

SCF has first-class AOT/native support and functions are an excellent fit for
native images — small, fast cold starts, ideal for FaaS. This guide documents it
rather than shipping a native build (which needs a GraalVM toolchain).

To make any of the app modules native, add the AOT/native plugins and build:

```kotlin
// build.gradle.kts (per app module)
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native") version "0.10.3"
}
```

```bash
./gradlew :app-web:nativeCompile      # produces a native executable
./app-web/build/native/nativeCompile/app-web
```

Notes specific to SCF + native:

- Register your POJOs for reflection if you rely on runtime type inspection.
  SCF's function type introspection is AOT-friendly, but custom converters and
  reflective payloads may need `@RegisterReflectionForBinding(Order.class)` (and
  friends) on the application class.
- Function **composition and routing resolve at runtime by name** — keep the
  function definitions in configuration so AOT can see them, and prefer explicit
  `spring.cloud.function.definition` over purely dynamic lookups where cold-start
  matters.
- The AWS adapter has a dedicated native/custom-runtime path; combine the Shadow
  jar guidance above with GraalVM's `native-image` for the smallest Lambda cold
  starts.

---

## Layout

```
spring-cloud-function-ultimate-guide/
├── functions-core/        the functions + POJOs + converter + dynamic registrar
├── app-web/               HTTP surface (:8080)
├── app-stream-kafka/      Kafka surface (+ DLQ, tracing, Testcontainers test)
├── app-rsocket/           RSocket surface (:7000)
├── adapter-aws/           AWS Lambda surface (local-only test)
├── docker-compose.yml     single-node Kafka (KRaft)
└── scripts/               run-demo · demo-requests · stop-demo
```

## Running the tests

```bash
./gradlew test            # all modules; Kafka test auto-skips without Docker
./gradlew :app-web:test   # one module
```

| Module | What its tests prove |
|---|---|
| `functions-core` | Composition, `Message` headers, reactive, multi-output tuple, dynamic registration, CSV converter — all via `FunctionCatalog`. |
| `app-web` | The same beans reachable over HTTP, ad-hoc composition, header routing, `text/csv`. |
| `app-stream-kafka` | Real-broker happy path **and** DLQ routing (Testcontainers). |
| `app-rsocket` | Request/response and request/channel over a real RSocket client. |
| `adapter-aws` | The real Lambda `FunctionInvoker` handler, in-process. |
