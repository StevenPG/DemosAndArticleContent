# The Ultimate Guide to Testcontainers with Spring Boot

Companion project for the article [Ultimate Guide to Testcontainers with Spring Boot](https://stevenpg.com/posts/ultimate-guide-testcontainers-spring-boot).

One realistic app (shipments: Postgres + Flyway, Kafka listener, external REST
API), tested end-to-end with real infrastructure:

| Test | Demonstrates |
|---|---|
| `ShipmentPersistenceTest` | Postgres 18 via `@ServiceConnection`, real Flyway migrations, real constraints |
| `ShipmentEventFlowTest` | Kafka → listener → Postgres async flow with Awaitility |
| `CarrierClientTest` | External API mocked with the WireMock Testcontainers module |
| `ContainersConfig` | Shared `@TestConfiguration` containers + `.withReuse(true)` |
| `TestTcDemoApplication` | `bootTestRun` — local dev against containers, zero installs |

## Running

Requires Docker.

```bash
./gradlew test          # full suite
./gradlew bootTestRun   # run the app locally against containerized Postgres+Kafka
```

## Container reuse (the CI timing trick)

Enable reuse once per machine:

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

With reuse on, containers survive between Gradle invocations; the second
`./gradlew test` run skips all container startup. Timing methodology and
numbers are in the article.
