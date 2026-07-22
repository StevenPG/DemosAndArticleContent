---
title: "Schema Registry with Spring Boot and Kafka: getting schema evolution right"
date: 2026-07-21
tags: [spring-boot, kafka, schema-registry, avro, java]
---

# Schema Registry with Spring Boot and Kafka: getting schema evolution right

Kafka doesn't care what you put in a message. To a broker, a record value is
just bytes. That's wonderful for throughput and awful for the person on the
consuming team who finds out at 2am that a producer quietly renamed a field.

A **Schema Registry** fixes that. It turns "just bytes" into a versioned,
enforceable contract, and — this is the part people underuse — it can *reject a
breaking change before it ever reaches production*. This post builds a small
Spring Boot 4 service around Confluent Schema Registry and Avro, and spends most
of its time on the question that actually matters day to day: **how do you
change a schema without breaking everyone downstream?**

The complete project is in this folder. If you just want to run it, the
[README](./README.md) has the commands. Here we'll walk through *why* it's
built the way it is.

## The problem, concretely

Two teams share an `orders` topic. The orders team produces `OrderEvent`
records; the fulfillment team consumes them. They deploy on their own schedules
and never talk to each other except through this topic.

One day the orders team decides `amount` should be a string ("to support
currencies with weird precision"). They deploy. Every fulfillment consumer that
expects a `double` starts throwing deserialization errors on the next message.
Nobody changed the fulfillment code. Nobody *could* have — they didn't know.

The registry's whole job is to make that deploy fail on the orders team's side,
at build time, with a clear message, instead of failing silently on the
fulfillment team's side at runtime.

## The moving parts

```
  POST /orders
        │
        ▼
  OrderProducer ──serialize (Avro)──►  Schema Registry   (schema id ⇄ schema)
        │                                     ▲
        │  <id + Avro bytes>                  │ "here's my writer schema"
        ▼                                     │
     Kafka topic  ──────────────────►  OrderEventListener ──deserialize──► store
```

The trick that makes this cheap: the message on the wire is **not** the schema.
Confluent's serializer writes a one-byte magic marker, a **4-byte schema id**,
and then the Avro payload. The schema itself is stored once in the registry. The
consumer reads the id, fetches the schema (and caches it), and decodes. You pay
the schema-transfer cost once, not per message.

## Wiring it up in Spring Boot 4

Spring Boot 4 ships a first-class `spring-boot-starter-kafka`, so the build is
short. The only non-obvious bits are the Confluent Maven repo and the Avro
codegen plugin:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.apache.avro:avro:1.12.0")
    implementation("io.confluent:kafka-avro-serializer:8.0.0")
    implementation("io.confluent:kafka-schema-registry-client:8.0.0")
}
```

### The schema is the source of truth

We define the contract as an `.avsc` file and let the build generate the Java
class. That ordering matters: the schema is the artifact you review, version,
and enforce — the Java class is a byproduct.

```json
{
  "type": "record",
  "name": "OrderEvent",
  "namespace": "com.example.schemaregistry.avro",
  "fields": [
    { "name": "orderId",    "type": "string" },
    { "name": "customerId", "type": "string" },
    { "name": "amount",     "type": "double" },
    { "name": "currency",   "type": "string", "default": "USD" },
    { "name": "status",
      "type": { "type": "enum", "name": "OrderStatus",
                "symbols": ["PLACED","PAID","SHIPPED","CANCELLED"], "default": "PLACED" },
      "default": "PLACED" },
    { "name": "createdAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

Notice the `default`s. They aren't decoration — they're what makes this schema
*evolvable*. We'll come back to that.

### Producer and consumer are boring (that's the point)

Once the serializers are configured, your application code never mentions the
registry. The producer just sends a generated object:

```java
@Component
public class OrderProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topic;

    public CompletableFuture<SendResult<String, OrderEvent>> send(OrderEvent event) {
        return kafkaTemplate.send(topic, event.getOrderId(), event);
    }
}
```

and the consumer just receives one:

```java
@KafkaListener(topics = "${app.topic}", groupId = "${spring.kafka.consumer.group-id}")
void onOrderEvent(OrderEvent event) {
    log.info("Consumed order {} for {}", event.getOrderId(), event.getCustomerId());
    store.add(event);
}
```

All the registry interaction is in configuration:

```yaml
spring:
  kafka:
    properties:
      schema.registry.url: http://localhost:8081
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    consumer:
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        specific.avro.reader: true   # decode into OrderEvent, not GenericRecord
```

## What the registry actually enforces

Here's the core idea, and it's worth stating precisely because people get it
backwards. Each topic gets a **subject** (`orders-value` by convention), and the
subject has a **compatibility mode**. The default, and the one you should
usually keep, is `BACKWARD`:

> **BACKWARD compatibility:** a consumer using the *new* schema can read data
> that was written with the *old* schema.

That's the guarantee that lets you upgrade consumers to a new schema while old
producers are still out there writing old data. It leads directly to a table you
should memorize:

| Change | Allowed under BACKWARD? | Why |
|---|---|---|
| Add a field **with a default** | ✅ | Reading old data, the new field falls back to its default |
| Remove a field | ✅ | The new reader simply ignores it |
| Add a field **without a default** | ❌ | Old data has no value and there's no fallback |
| Change a field's type | ❌ | Old `double` bytes can't be read as a `string` |
| Rename a field (no alias) | ❌ | It reads as "old field removed, new field added-without-default" |

The other modes are variations on the same theme: `FORWARD` flips the direction
(old readers can read new data), and `FULL` demands both. If your producers and
consumers deploy independently and you want the strongest safety net, `FULL` is
the honest choice — but `BACKWARD` is the pragmatic default and where most teams
live.

## Proving it, not just claiming it

This is where the demo earns its keep. Anyone can write a blog post that *says*
"the registry rejects breaking changes." The project has a Testcontainers test
that stands up a real Kafka broker and a real Schema Registry and makes the
registry answer.

The evolution test registers v1 under a fresh subject, then tries two changes.
The compatible one — adding an optional `loyaltyTier` — is accepted:

```java
@Test
void addingAnOptionalFieldIsBackwardCompatible() throws Exception {
    AvroSchema v2 = new AvroSchema(V2_ADD_OPTIONAL_FIELD);

    assertThat(client.testCompatibility(subject, v2)).isTrue();   // dry run: yes

    int newId = client.register(subject, v2);                     // and it registers
    assertThat(client.getAllVersions(subject)).containsExactly(1, 2);
}
```

The breaking one — `amount` from `double` to `string` — is refused, with the
same HTTP 409 your CI pipeline would see:

```java
@Test
void changingAFieldTypeIsRejected() {
    AvroSchema broken = new AvroSchema(INCOMPATIBLE_TYPE_CHANGE);

    assertThat(compatible(broken)).isFalse();                     // dry run: no

    assertThatThrownBy(() -> client.register(subject, broken))
        .isInstanceOf(RestClientException.class)
        .satisfies(ex -> assertThat(((RestClientException) ex).getStatus()).isEqualTo(409));
}
```

The optional field, for reference, is the whole game in one line:

```json
{ "name": "loyaltyTier", "type": ["null", "string"], "default": null }
```

Nullable, with a default. Old data that never had a `loyaltyTier` reads back as
`null`. That's why it's safe, and it's the shape of *almost every* schema change
you'll ever make: additive, optional, defaulted.

Testcontainers means there's no local setup and no mock pretending to be a
registry — the base test class starts the real thing once for the run:

```java
static final ConfluentKafkaContainer KAFKA =
        new ConfluentKafkaContainer("confluentinc/cp-kafka:8.0.0")
                .withListener("kafka:19092")
                .withNetwork(NETWORK);

static final GenericContainer<?> SCHEMA_REGISTRY =
        new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:8.0.0"))
                .withNetwork(NETWORK)
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
```

## The change process (the part to steal)

Everything above is setup for the workflow. Here's how a team ships a schema
change without a war room.

**1. Edit the `.avsc` and open a pull request.** The schema is code. It gets
reviewed like code. Reviewers can reason about the change because the diff is
the contract, not a Java class three layers deep.

**2. CI runs a compatibility check against the registry — a dry run.** This is
the gate. The `POST .../compatibility/...` endpoint tells you yes/no *without*
registering anything:

```bash
./scripts/check-compatibility.sh orders-value evolution/order-event-v2-backward-compatible.avsc
# → { "is_compatible": true }

./scripts/check-compatibility.sh orders-value evolution/order-event-incompatible.avsc
# → { "is_compatible": false }
```

Fail the build when `is_compatible` is `false`. Now a breaking change *cannot*
merge. The 2am incident from the intro becomes a red check on a PR.

**3. Roll out in the compatibility-appropriate order.** Under `BACKWARD` you
deploy **consumers first** (they learn to read the new field), then producers
(they start writing it). Register the new version as part of that rollout:

```bash
./scripts/register-schema.sh orders-value evolution/order-event-v2-backward-compatible.avsc
```

**4. In production, don't auto-register.** This demo sets
`auto.register.schemas=true` so you can play with it in thirty seconds. On a
real system, set it to `false`. Auto-registration means any producer can invent
a schema at runtime — which quietly moves the source of truth from your reviewed
`.avsc` files into whatever happened to deploy last. Register from CI instead,
and the registry stays a deliberate, reviewed artifact.

That's the whole discipline:

- schema in version control,
- compatibility checked in CI,
- registration in the pipeline,
- rollout ordered by compatibility mode.

Four rules, and the class of "someone broke the topic" incidents essentially
disappears.

## Running it yourself

```bash
docker compose up -d      # Kafka + Schema Registry + Kafka UI
./gradlew bootRun

curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"customer-42","amount":129.99,"currency":"USD"}'

curl -s http://localhost:8080/orders/received | jq .
```

Open Kafka UI at <http://localhost:8080> to watch the `orders` topic fill up and
see the `orders-value` schema the producer registered. Then try registering the
incompatible schema by hand and watch the registry say no.

Or skip all of that and just run:

```bash
./gradlew test
```

The tests start their own infrastructure and prove the whole story in about the
time it takes to read this paragraph.

## Takeaways

- A Schema Registry turns a Kafka topic from "bytes and hope" into an enforced,
  versioned contract, for the price of a 5-byte header per message.
- With Spring Boot 4, the application code stays boring — produce and consume
  generated objects; the registry lives entirely in configuration.
- `BACKWARD` compatibility plus "additive, optional, defaulted" covers the vast
  majority of real schema changes.
- The value isn't the wiring, it's the **process**: schema in git, compatibility
  checked in CI, registration in the pipeline. That's what stops the 2am page.
