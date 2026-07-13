# Flyway vs Liquibase in 2026

Companion project for the article [Flyway vs Liquibase in 2026: Which Should You Pick?](https://stevenpg.com/posts/flyway-vs-liquibase-2026).

The **same schema evolved through the same 10 migrations** in both tools, so
the differences are the tools' — not the SQL's:

| Step | Change |
|---|---|
| 1–2 | `customers` and `orders` tables |
| 3 | index on `orders.customer_id` |
| 4–5 | add `status` column + data backfill |
| 6–8 | expand/contract split of `customers.name` into first/last |
| 9 | reporting view |
| 10 | money → integer cents (with dependent-view rebuild) |

- **`flyway-demo/`** — plain versioned SQL (`V1__...` through `V10__...`)
- **`liquibase-demo/`** — formatted SQL changesets, each with a `--rollback`
  block (the thing Flyway OSS doesn't give you)

## Running

Each demo verifies its migrations against PostgreSQL 18 via Testcontainers:

```bash
(cd flyway-demo && ./gradlew test)
(cd liquibase-demo && ./gradlew test)
```

The rollback and drift-detection walkthroughs (where the tools genuinely
differ) are in the article, using the Liquibase CLI against these changelogs.
