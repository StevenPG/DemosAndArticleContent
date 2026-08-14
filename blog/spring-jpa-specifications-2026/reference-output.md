# Spring Data JPA Specifications in 2026 — Reference Output

Captured on 2026-08-01 by running the project end to end:

```bash
./gradlew test          # 44 tests, 0 failures
docker compose up -d
./gradlew bootRun
./scripts/demo-requests.sh
```

Environment: Linux x86_64, 4 cores, OpenJDK 21.0.10, Gradle 9.6, Docker 29.3.1,
PostgreSQL 18 (`postgres:18-alpine`), Spring Boot 4.1.0 / Spring Data JPA 4.1.0 /
Hibernate 7.4.1.Final / Testcontainers 2.0.5.

Five defects had to be fixed before this run was possible; they are listed at the bottom.

## 1. Test suite

`./gradlew test` — **BUILD SUCCESSFUL in 57s**, one PostgreSQL container for the whole run.

| Test class | Tests | Failures | Time |
| --- | ---: | ---: | ---: |
| `CompositionTests` | 7 | 0 | 4.82s |
| `DynamicSearchTests` | 5 | 0 | 3.29s |
| `JoinAndSubqueryTests` | 8 | 0 | 5.10s |
| `InheritanceAndFunctionTests` | 8 | 0 | 5.07s |
| `FetchJoinAndCountQueryTests` | 5 | 0 | 2.96s |
| `FluentQueryAndScrollTests` | 6 | 0 | 3.65s |
| `BulkUpdateDeleteTests` | 5 | 0 | 16.70s |
| **Total** | **44** | **0** | |

Every assertion value in the suite is derived from `SampleData`'s 120 deterministic flights
and now matches what PostgreSQL actually returns.

## 2. Application startup

```
Started SpecificationsApplication in 5.139 seconds (process running for 5.562)
Tomcat started on port 8080 (http) with context path '/'
```

`SampleDataLoader` seeds the 120 flights in one transaction at startup.

## 3. `./scripts/demo-requests.sh` — the narrowing

One endpoint, one repository method, twenty optional filters. Each row adds a condition:

| Panel | Query | Matched |
| --- | --- | ---: |
| No filters | `?size=1` | **120** |
| One | `?origin=ATL` | **15** |
| Two | `+ maxPrice=300` | **7** |
| Three | `+ airlineCodes=DL,UA` | **6** |
| Four | `+ requiredAmenities=WIFI,POWER` | **3** |
| Five | `origin=ATL, maxPrice=300, POWER, cabinClass=BUSINESS, minSeatsAvailable=10` | **2** |
| jsonb | `?wifiVendor=Starlink` | **30** |
| `date_part()` | `?earliestDepartureHourUtc=6&latestDepartureHourUtc=11` | **30** |
| EXISTS subquery | `?bookedByTier=PLATINUM` | **52** |

First hit of the unfiltered search:

```json
{
  "id": 1,
  "type": "PASSENGER",
  "flightNumber": "DL1000",
  "origin": "ATL",
  "destination": "ORD",
  "distanceKm": 600,
  "departureTime": "2026-08-01T00:00:00Z",
  "arrivalTime": "2026-08-01T01:35:00Z",
  "basePrice": 120.00,
  "status": "SCHEDULED",
  "airline": "DL",
  "aircraft": "A320neo",
  "amenities": ["MEAL", "POWER"],
  "seatsAvailable": 4,
  "maxPayloadKg": null
}
```

DTO projection, `GET /api/flights/summaries?origin=ATL&size=3` — narrowed SELECT list, no
entities hydrated:

```json
[
  {"id": 1,  "flightNumber": "DL1000", "departureTime": "2026-08-01T00:00:00Z", "arrivalTime": "2026-08-01T01:35:00Z", "basePrice": 120.00, "status": "SCHEDULED"},
  {"id": 9,  "flightNumber": "AA1008", "departureTime": "2026-08-01T08:00:00Z", "arrivalTime": "2026-08-01T10:15:00Z", "basePrice": 480.00, "status": "SCHEDULED"},
  {"id": 17, "flightNumber": "UA1016", "departureTime": "2026-08-01T16:00:00Z", "arrivalTime": "2026-08-01T18:55:00Z", "basePrice": 435.00, "status": "SCHEDULED"}
]
```

Keyset scrolling, `GET /api/flights/scroll?origin=ATL&size=4` returns ids 1, 9, 17, 25; the
cursor built from the last row (`afterDeparture=2026-08-02T00:00:00Z&afterId=25`) continues
with ids 33, 41, 49, 57 — no OFFSET involved.

## 4. Generated SQL

Two Criteria functions and a column predicate folding into one statement
(`?origin=ATL&wifiVendor=Starlink&earliestDepartureHourUtc=6&latestDepartureHourUtc=11`):

```sql
select
    f1_0.id, f1_0.dtype, f1_0.airline_id, f1_0.aircraft_id, f1_0.arrival_time,
    f1_0.base_price, f1_0.departure_time, f1_0.flight_number, f1_0.metadata,
    f1_0.destination, f1_0.distance_km, f1_0.origin, f1_0.status,
    f1_0.hazmat_certified, f1_0.max_payload_kg, f1_0.seats_available
from
    flight f1_0
left join
    airline a1_0 on a1_0.id=f1_0.airline_id
left join
    aircraft a2_0 on a2_0.id=f1_0.aircraft_id
where
    f1_0.origin=?
    and date_part('hour', f1_0.departure_time) between ? and ?
    and jsonb_extract_path_text(f1_0.metadata, 'wifiVendor')=?
order by
    f1_0.departure_time
offset
    ? rows
fetch
    first ? rows only
```

```
binding parameter (1:VARCHAR) <- [ATL]
binding parameter (2:DOUBLE)  <- [6.0]
binding parameter (3:DOUBLE)  <- [11.0]
binding parameter (4:VARCHAR) <- [Starlink]
binding parameter (5:INTEGER) <- [0]
binding parameter (6:INTEGER) <- [1]
```

The derived count query keeps the predicates and drops the joins the fetch guard excluded:

```sql
select
    count(f1_0.id)
from
    flight f1_0
where
    f1_0.origin=?
    and date_part('hour', f1_0.departure_time) between ? and ?
    and jsonb_extract_path_text(f1_0.metadata, 'wifiVendor')=?
```

## 5. What had to be fixed to get here

The project as originally committed did not run. In order of severity:

1. **`SampleDataLoader` never ran inside a transaction.** `@Transactional` was on a method of
   an `@Configuration` class, invoked from a lambda returned by its own `@Bean` method. Self
   invocation bypasses the proxy, so the first `em.persist(...)` threw
   `TransactionRequiredException` — the app failed to start and every test failed to load its
   context. It is now a `@Component implements CommandLineRunner`, which Spring calls through
   the proxy.
2. **The seeding runner also ran in tests**, which would have left every test class starting
   from 240 flights instead of 120. It is now `@Profile("!test")`, and `AbstractPostgresTest`
   is `@ActiveProfiles("test")`.
3. **`@Testcontainers` + `@Container` stopped the container after every test class.** The
   JUnit extension starts the static field in `beforeAll` and stops it in `afterAll` of each
   subclass, while Spring cached one application context across all eight classes. The second
   class onwards got a new container on a new random port while the cached `DataSource` still
   pointed at the dead one — `SQLTransientConnectionException`, 43 of 44 tests failing. Now
   the singleton-container pattern: started once from a static initialiser, never stopped,
   reaped by Ryuk at JVM exit. Side benefit: the suite went from 3m25s to 57s.
4. **`updateSpecificationComposition` asserted the wrong invariant.** It compared the count of
   `SEA + DELAYED` after a bulk update against the affected-row count alone (13), but the
   dataset already contained one delayed SEA flight, so the real count is 14. It now records
   the pre-existing count and asserts the sum.
5. **`Page` responses serialized in the unstable `PageImpl` shape**, putting `totalElements`
   at the top level while `demo-requests.sh` read `.page.totalElements` — every panel printed
   `"matched": null`. Spring Data logs a warning about exactly this. `application.yml` now
   sets `spring.data.web.pageable.serialization-mode: via-dto`.

Two demo queries were also retargeted because `SampleData`'s generators are correlated in
ways that made the panels meaningless:

- WIFI is added when `i % 3 != 0` and the BUSINESS cabin when `i % 3 == 0`, so **no flight in
  the dataset has both**. Panel five asked for both and always matched zero; it now asks for
  POWER, which every BUSINESS flight has.
- `wifiVendor` comes from `i % 4` and `origin` from `i % 8`, so the vendor is constant within
  any single airport: `origin=ATL&wifiVendor=Starlink` matches all 15 ATL flights and
  `origin=ORD&wifiVendor=Starlink` matches none. The jsonb panel dropped the origin filter and
  now shows 30 of 120.

Worth knowing before quoting counts from this dataset in the post — the same two correlations
constrain any other example built on it.
