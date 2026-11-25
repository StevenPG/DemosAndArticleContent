# Spring Boot 4.1 → 4.2: What's New and What Breaks

Companion project for [Spring Boot 4.1 → 4.2: What's New and What Breaks](https://stevenpg.com/posts/spring-boot-4-2-whats-new-what-breaks).

A deliberately tiny Spring MVC app used to check the post's claims on a real build, plus an identical
baseline app built against both 4.1 and 4.2 for side-by-side behavior and startup comparisons.

| Piece            | Version                                    |
| ---------------- | ------------------------------------------ |
| Spring Boot      | **4.2.0-RC2** (and 4.1.2 for the baseline) |
| Spring Framework | **7.1.0-RC2** (7.0.10 on 4.1.2)            |
| Java             | 25                                         |
| Build            | Maven (wrapper included)                   |

## What it demonstrates

The main app (project root, Boot 4.2):

1. **HTTP `QUERY`**: `DemoController` maps `RequestMethod.QUERY` on `/products/search` with a JSON body.
2. **Problem details for every unhandled exception**: `WebConfig` calls
   `ProblemDetailsConfigurer.renderUnhandledExceptions(true)`, so `/boom` returns `application/problem+json`
   without leaking the exception message.
3. **`RestTemplate` deprecation**: `LegacyClient` injects `RestTemplateBuilder`. Compile with
   `-Dmaven.compiler.showDeprecation=true` to see the "deprecated and marked for removal" warnings.
4. **`RestTestClient` replacing `TestRestTemplate`**: `Boot42ApplicationTests` uses
   `@AutoConfigureRestTestClient`, including a `QUERY` request and a problem-detail assertion.
5. **Properties migrator fail mode**: `spring-boot-properties-migrator` is on the runtime classpath.

`startup-baseline/boot-4.1` and `startup-baseline/boot-4.2` contain the **same source code** (the main app without
the 4.2-only `QUERY` mapping and `WebConfig`) with different Boot parents.

## Running it

```bash
# Tests (QUERY, RestTestClient, problem details)
./mvnw test

# Run the app
./mvnw spring-boot:run

curl -X QUERY -H 'Content-Type: application/json' \
  -d '{"term":"boots","limit":5}' localhost:8080/products/search
curl -i localhost:8080/boom

# Properties migrator: a key that moved in 4.2 now fails startup
./mvnw -q -DskipTests package
java -jar target/boot42-0.0.1-SNAPSHOT.jar \
  --server.tomcat.use-relative-redirects=false \
  --spring.properties.migrator.fail=on-warning

# 4.1 vs 4.2: malformed Accept headers (200 → 406) and relative redirects
./scripts/behavior-diff.sh

# 4.1 vs 4.2: startup time, 12 alternating runs, prints medians
./scripts/compare-startup.sh 12
```

## Reference results

From an Apple M3 Pro with Temurin 25, on 4.1.2 vs 4.2.0-RC2 (raw runs in
[`startup-baseline/raw-results-rc2.txt`](startup-baseline/raw-results-rc2.txt)):

| Check                                         | 4.1.2                          | 4.2.0-RC2 |
| --------------------------------------------- | ------------------------------ | --------- |
| `Accept: text/plain;charset=UTF-8;CHARSET=…`  | 200                            | 406       |
| `Accept: text/plain;foo`                      | 200                            | 406       |
| `redirect:/hello` → `Location`                | `http://localhost:18081/hello` | `/hello`  |
| Startup, `Started … in` (median, n=12)        | 1.011 s                        | 0.950 s   |
| Startup, `process running for` (median, n=12) | 1.206 s                        | 1.116 s   |
