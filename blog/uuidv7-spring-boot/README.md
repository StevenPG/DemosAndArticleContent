# UUIDv7 in Spring Boot and Postgres

Companion project for the article [UUIDv7 in Spring Boot and Postgres: The Right Way (2026)](https://stevenpg.com/posts/uuidv7-in-spring-boot-and-postgres).

Two things live here:

1. **`Order` entity** — the one-line Hibernate setup for UUIDv7 primary keys
   (`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`), verified by
   `OrderUuidv7Test` against PostgreSQL 18 in Testcontainers.
2. **`IdBenchmarkTest`** — a reproducible benchmark comparing insert throughput,
   index size, and B-tree leaf fragmentation across `bigint`, UUIDv4, and UUIDv7
   primary keys (1M rows, plain JDBC, PostgreSQL 18).

## Running

Requires Docker (for Testcontainers). The Java 25 toolchain is auto-provisioned
by Gradle.

```bash
# JPA/Hibernate verification tests (fast)
./gradlew test --tests OrderUuidv7Test

# Full 1M-row benchmark (takes a few minutes)
./gradlew test --tests IdBenchmarkTest
```

The benchmark prints insert timings and per-index `pgstatindex` fragmentation to
stdout. Absolute numbers vary by machine; the ratios (uuid4 slowest, ~50%
fragmented; uuid7 within ~15% of bigint, ~0% fragmented) should reproduce
anywhere.
