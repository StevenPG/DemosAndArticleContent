# Spring Data JPA Specifications in 2026

Companion project for [The Spring Data JPA Specification API in 2026](https://stevenpg.com/posts/spring-jpa-specification-api-2026).

A flight-search service built the way you would actually build one: a single REST endpoint with
twenty optional filters, served by one repository method, backed by composed `Specification`s
over the Jakarta Persistence Criteria API.

| Piece                | Version                   |
| -------------------- | ------------------------- |
| Spring Boot          | **4.1.0**                 |
| Spring Data JPA      | **4.1.0** (BOM 2026.0.0)  |
| Hibernate ORM        | **7.4.1.Final**           |
| Jakarta Persistence  | **3.2.0**                 |
| Java                 | 21                        |
| Gradle               | 9.6                       |
| PostgreSQL           | 18                        |
| Testcontainers       | **2.0.5**                 |

## What it demonstrates

The domain (`Flight` and friends) is deliberately over-mapped so that every Criteria shape has
somewhere to go: single-table inheritance with two subclasses, an `@Embedded` value type, a
`@ManyToOne`, a `@ManyToMany`, a `@OneToMany`, an `@ElementCollection` of enums, and a real
PostgreSQL `jsonb` column.

`FlightSpecifications` is the cookbook. In order:

1. **Column predicates** as `PredicateSpecification` — the query-agnostic interface added in 4.0.
2. **Embeddable navigation** — `root.get(Flight_.route).get(Route_.origin)`.
3. **Joins** — implicit path navigation vs. an explicit `join()` alias.
4. **Collection membership** — `EXISTS` instead of a join, plus a counting subquery for
   "has *all* of these amenities" and the join version for comparison.
5. **Subqueries** — uncorrelated, correlated via `sub.correlate(root)`, and `NOT EXISTS`.
6. **Inheritance** — `root.type()` against the discriminator, `cb.treat()` to reach subclass
   attributes, `isMember` against an `@ElementCollection`.
7. **Database functions** — `cb.function()` for `date_part` and `jsonb_extract_path_text`, plus
   Hibernate's own `HibernateCriteriaBuilder.ilike()`.
8. **Fetch joins** — and the `getResultType()` guard that keeps them out of the derived count
   query.
9. **Grouping** — the one place Specifications fight you, with the subquery alternative.
10. **Bulk update and delete** — `UpdateSpecification` / `DeleteSpecification`.

`FlightSpecificationBuilder` folds a `FlightSearchCriteria` record into one `Specification`,
starting from `Specification.unrestricted()`. `FlightSearchService` shows offset pagination with
a guarded fetch join, DTO projections through `findBy(spec, q -> q.as(...))`, and keyset
scrolling through `.scroll(ScrollPosition)`.

## Running it

```bash
docker compose up -d
./gradlew bootRun

# in another shell
./scripts/demo-requests.sh
```

The app seeds 120 deterministic flights on startup (`SampleData`), so every number in the blog
post is reproducible.

## Running the tests

```bash
./gradlew test
```

Requires a working Docker daemon — the suite runs against a real PostgreSQL 18 container via
Testcontainers, because half of what the guide covers (`jsonb`, `date_part`, `ilike`, the exact
count-query failure) does not exist on an in-memory database.

> **Provenance:** the whole project — including every test — is compile-verified against the
> versions in the table above. The test suite itself was authored without a Docker daemon
> available, so it has not been executed end to end. The assertion values are derived from
> `SampleData`, which is deterministic and was evaluated directly.

## Notes worth stealing

- `hibernate-jpamodelgen` is **not** managed by the Spring Boot BOM. The `annotationProcessor`
  entry needs an explicit version, pinned to whatever Hibernate version Boot brings in.
- Testcontainers 2.x renamed every module. `org.testcontainers:postgresql` is now
  `org.testcontainers:testcontainers-postgresql`, and `PostgreSQLContainer` moved to
  `org.testcontainers.postgresql` and lost its self-referential generic.
- Schema comes from `ddl-auto: create-drop` so the mapping can never drift from the DDL in a
  demo. Use a migration tool in anything real — see
  [Flyway vs Liquibase in 2026](https://stevenpg.com/posts/flyway-vs-liquibase-2026).
