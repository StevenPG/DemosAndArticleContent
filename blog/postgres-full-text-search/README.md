# Do You Really Need Elasticsearch?

Companion project for the article [Do You Really Need Elasticsearch? Postgres Full-Text Search in Spring Boot](https://stevenpg.com/posts/postgres-full-text-search-vs-elasticsearch).

The same search implemented three ways against one Postgres table:

| Endpoint           | Strategy                                        | Index                   |
|--------------------|-------------------------------------------------|-------------------------|
| `/search/ilike?q=` | `ILIKE '%q%'` baseline                          | none (seq scan)         |
| `/search/fts?q=`   | `tsvector` + `websearch_to_tsquery` + `ts_rank` | GIN on generated column |
| `/search/fuzzy?q=` | `pg_trgm` similarity (typo-tolerant)            | GIN `gin_trgm_ops`      |

Flyway seeds 50,000 articles so the latency numbers mean something.

## Running

```bash
docker compose up -d
./gradlew bootRun

curl 'localhost:8080/search/fts?q=postgres%20indexing'
curl 'localhost:8080/search/fuzzy?q=Postgress%20performence'   # typos on purpose
```

## Latency benchmark

Requires Docker (Testcontainers):

```bash
./gradlew test --tests SearchLatencyTest
```

Prints p50/p99 latency per strategy over 500 iterations against the 50k-row
corpus.
