# Rate Limiting in Spring Boot with Bucket4j and Redis

Companion project for the article [Rate Limiting in Spring Boot with Bucket4j and Redis](https://stevenpg.com/posts/rate-limiting-spring-boot-bucket4j-redis).

Distributed, two-tier rate limiting on Spring Boot 4:

- **Per API key** (`X-Api-Key` header): 100 req/min, burst 120
- **Per IP** (anonymous): 20 req/min
- Bucket state in Redis via Bucket4j's Lettuce proxy manager — limits hold
  across every instance of the app
- Proper `429` responses with `Retry-After` and `X-Rate-Limit-Remaining`
  headers

## Running

```bash
docker compose up -d      # Redis
./gradlew bootRun

# Watch the 21st request within a minute get a 429:
for i in $(seq 1 25); do curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/quote; done
```

## Load test with k6

```bash
k6 run k6/rate-limit-test.js
```

Two scenarios run side by side: anonymous traffic at 3x its limit (expect ~2/3
429s) and API-key traffic just under its limit (expect ~zero 429s).

## Integration tests

Requires Docker (Testcontainers):

```bash
./gradlew test
```
