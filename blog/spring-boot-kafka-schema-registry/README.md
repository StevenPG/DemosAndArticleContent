# Spring Boot 4 + Kafka + Schema Registry (Avro)

Companion project for the article **[Schema Registry with Spring Boot and Kafka: getting schema evolution right](https://stevenpg.com/posts/spring-boot-kafka-schema-registry)**.

A small `orders` service that produces and consumes Avro `OrderEvent` records
through a **Confluent Schema Registry**, plus Testcontainers tests that prove
what the registry actually enforces when a schema changes.

The narrative walkthrough lives in [BLOG.md](./BLOG.md). This README is the
"how do I run it" reference.

## What's in here

| Piece                                  | What it shows                                                                              |
|----------------------------------------|--------------------------------------------------------------------------------------------|
| `src/main/avro/OrderEvent.avsc`        | The Avro schema. Build-time codegen turns it into a Java class.                            |
| `OrderProducer` / `OrderEventListener` | Avro serialize/deserialize wired through Spring's Kafka starter.                           |
| `OrderController`                      | `POST /orders` to publish, `GET /orders/received` to see what was consumed.                |
| `OrderFlowIntegrationTest`             | Full round trip against **real** Kafka + Schema Registry (Testcontainers).                 |
| `SchemaEvolutionTest`                  | The important one: a compatible change is accepted, a breaking one is rejected with `409`. |
| `docker-compose.yml`                   | Kafka (KRaft) + Schema Registry + Kafka UI for hands-on play.                              |
| `evolution/` + `scripts/`              | Standalone schemas and curl helpers for driving the registry REST API.                     |

## Requirements

- JDK 25 (the Gradle toolchain will fetch it if you don't have it)
- Docker (for `docker compose` and the Testcontainers tests)

## Run the tests (no manual setup)

Testcontainers starts everything for you:

```bash
./gradlew test
```

`SchemaEvolutionTest` is the one to read — it registers v1, then proves that
adding an optional field is accepted and that changing a field's type is refused.

## Run it locally and poke at it

```bash
docker compose up -d      # Kafka, Schema Registry, Kafka UI
./gradlew bootRun
```

Publish an order:

```bash
curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"customer-42","amount":129.99,"currency":"USD"}'
```

See what the consumer read back:

```bash
curl -s http://localhost:8080/orders/received | jq .
```

Handy URLs while the stack is up:

- Kafka UI — <http://localhost:8092> (browse the `orders` topic and the registered schemas)
- Schema Registry REST — <http://localhost:8081>

Inspect the registry directly:

```bash
curl -s http://localhost:8081/subjects | jq .                       # subjects
curl -s http://localhost:8081/subjects/orders-value/versions | jq . # versions of the orders value schema
curl -s http://localhost:8081/config | jq .                         # global compatibility mode
```

## The schema change process

This is the workflow the article is really about — how a team ships a schema
change safely.

**1. Compatibility is a contract, and BACKWARD is the default.** With `BACKWARD`
compatibility, a consumer on the *new* schema must be able to read data written
with the *old* schema. In practice that means:

| Change                                       | Allowed under BACKWARD?   |
|----------------------------------------------|---------------------------|
| Add a field **with a default** (or nullable) | ✅ Yes                     |
| Remove a field                               | ✅ Yes (reader ignores it) |
| Add a field **without a default**            | ❌ No                      |
| Change a field's type                        | ❌ No                      |
| Rename a field (no alias)                    | ❌ No                      |

**2. Check before you merge.** Point the compatibility check at the running
registry — this is a dry run, nothing is registered:

```bash
# passes: adds an optional field
./scripts/check-compatibility.sh orders-value evolution/order-event-v2-backward-compatible.avsc
# fails: changes amount from double to string
./scripts/check-compatibility.sh orders-value evolution/order-event-incompatible.avsc
```

Wire the *passing* version of that command into CI (fail the build if
`is_compatible` is `false`) and a breaking change can never reach `main`.

**3. Register, then roll out in the right order.** Under `BACKWARD` you deploy
**consumers first**, then producers:

```bash
./scripts/register-schema.sh orders-value evolution/order-event-v2-backward-compatible.avsc
```

**4. In production, turn off auto-registration.** This demo uses
`auto.register.schemas=true` for convenience. On a real system, set it to
`false` and register schemas from CI so the registry is the source of truth and
no app can sneak a schema in at runtime.

Changing the mode itself (per subject):

```bash
curl -s -X PUT http://localhost:8081/config/orders-value \
  -H 'Content-Type: application/vnd.schemaregistry.v1+json' \
  -d '{"compatibility": "FULL"}' | jq .
```

`FULL` requires both backward *and* forward compatibility — the strictest common
choice when producers and consumers deploy independently.

## Clean up

```bash
docker compose down -v
```
