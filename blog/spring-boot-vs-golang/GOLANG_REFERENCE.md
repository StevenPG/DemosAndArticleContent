# The Go reference: how each Spring feature maps to Go

This document walks the Spring Boot service concern by concern and shows the Go
equivalent. The running theme: **Spring gives you behavior through
auto-configuration and annotations; Go gives you the same behavior through code
you write and can read top to bottom.** Neither is "better" — but they feel
very different, and knowing the mapping is what lets a Spring developer be
productive in Go quickly.

A useful mental model:

> **Spring Boot's `@SpringBootApplication` + component scan is replaced by Go's
> `func main()`.** Everything auto-configuration does implicitly — build a
> datasource, start Kafka listeners, register a JWT decoder, wire beans
> together — you do explicitly in `main()`. It's longer, but there is no magic
> and nothing you didn't write executes.

Compare [`spring-app/src/main/java/com/example/orders/OrdersApplication.java`](./spring-app/src/main/java/com/example/orders/OrdersApplication.java)
(a class with three annotations) against
[`go-app/main.go`](./go-app/main.go) (a `run()` function that constructs and
connects every component by hand). That contrast repeats for every feature
below.

---

## 1. Dependency injection & application wiring

**Spring:** Component scanning discovers `@Service`, `@Component`,
`@RestController`, `@Repository` beans and injects them via constructors. You
never call `new`.

```java
@Service
public class OrderService {
    public OrderService(OrderRepository repository, PaymentClient paymentClient,
                        InventoryClient inventoryClient, OrderEventPublisher eventPublisher) { ... }
}
```

**Go:** There is no container. You call the constructors yourself in `main()`
and pass dependencies in. The "wiring" is just ordinary function calls, and the
dependency graph is literally the order of statements in `run()`.

```go
repo := orders.NewRepository(pool)
service := orders.NewService(repo, payment, inventory, producer, log)
consumer := messaging.NewConsumer(cfg.KafkaBrokers, cfg.OrdersTopic, cfg.ConsumerGroup, service, log)
```

**Key idea:** In Go, interfaces are declared by the *consumer*, not the
implementer. `orders.Service` defines the small `PaymentCharger`,
`InventoryReserver`, and `EventPublisher` interfaces it needs
([service.go](./go-app/internal/orders/service.go)); the concrete clients
satisfy them implicitly (no `implements` keyword). This is what makes Go
testable without a DI framework — pass a fake that satisfies the interface.

---

## 2. REST API

**Spring:** `@RestController` + `@GetMapping`/`@PostMapping`. Method parameters
and return values are bound and serialized automatically.

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) { ... }
```

**Go:** The standard library's `net/http`. Since Go 1.22 the default
`ServeMux` supports method + path patterns with wildcards, so no third-party
router is needed. JSON encoding/decoding is explicit `encoding/json` calls.

```go
mux.Handle("POST /api/orders", verifier.RequireScope("orders:write", http.HandlerFunc(h.create)))
mux.Handle("GET /api/orders/{id}", verifier.RequireScope("orders:read", http.HandlerFunc(h.get)))

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
    var req orders.CreateOrderRequest
    if err := json.NewDecoder(r.Body).Decode(&req); err != nil { ... }
    // ...
    writeJSON(w, http.StatusCreated, order)
}
```

`r.PathValue("id")` reads the `{id}` wildcard — the equivalent of
`@PathVariable`. Files:
[OrderController.java](./spring-app/src/main/java/com/example/orders/api/OrderController.java)
vs [httpapi.go](./go-app/internal/httpapi/httpapi.go).

---

## 3. Validation

**Spring:** Bean Validation annotations on the DTO; `@Valid` triggers them and
a `MethodArgumentNotValidException` is thrown on failure.

```java
public record CreateOrderRequest(
    @NotBlank @Email String customerEmail,
    @Min(1) @Max(1000) int quantity,
    @Positive long totalCents) {}
```

**Go:** There's no annotation processor, so validation is a method you write
and call. Many Go teams use a library (`go-playground/validator` with struct
tags) that looks closer to Spring; this project shows the dependency-free
version to make the contrast explicit.

```go
func (r CreateOrderRequest) Validate() map[string]string {
    errs := map[string]string{}
    if _, err := mail.ParseAddress(r.CustomerEmail); err != nil {
        errs["customerEmail"] = "must be a well-formed email address"
    }
    if r.Quantity < 1 || r.Quantity > 1000 {
        errs["quantity"] = "must be between 1 and 1000"
    }
    return errs
}
```

The handler calls `req.Validate()` and returns a 400 if the map is non-empty.
Files: [CreateOrderRequest.java](./spring-app/src/main/java/com/example/orders/api/CreateOrderRequest.java)
vs [order.go](./go-app/internal/orders/order.go).

---

## 4. Error handling

**Spring:** `@RestControllerAdvice` with `@ExceptionHandler` methods centralizes
error mapping; `ProblemDetail` produces RFC 9457 responses.

**Go:** No global handler exists — each handler maps its own errors. A shared
`writeProblem` helper produces the same `application/problem+json` shape, and
sentinel errors (`errors.Is(err, orders.ErrNotFound)`) decide the status.

```go
order, err := h.service.Get(r.Context(), id)
if errors.Is(err, orders.ErrNotFound) {
    writeProblem(w, http.StatusNotFound, "Order "+id.String()+" not found", nil)
    return
}
```

**Trade-off:** Spring's advice is DRY but action-at-a-distance; Go's is
repetitive but local and obvious. Files:
[GlobalExceptionHandler.java](./spring-app/src/main/java/com/example/orders/api/GlobalExceptionHandler.java)
vs the `writeProblem` helper in [httpapi.go](./go-app/internal/httpapi/httpapi.go).

---

## 5. Persistence

**Spring:** Spring Data JPA. Extend `JpaRepository<Order, UUID>` and derived
queries are generated from method names; Hibernate maps the `@Entity` to SQL.

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
```

**Go:** No ORM in this build. A repository struct holds a `pgxpool.Pool` and
every query is hand-written SQL. What Spring generates from a method name, you
write out:

```go
func (r *Repository) List(ctx context.Context, status *Status) ([]Order, error) {
    query := `SELECT ` + orderColumns + ` FROM orders`
    if status != nil {
        query += ` WHERE status = $1 ORDER BY created_at DESC`
    }
    rows, err := r.pool.Query(ctx, query, args...)
    // ... scan each row into an Order
}
```

> Go *does* have ORMs (GORM, ent) and query builders (sqlc, squirrel). The
> mainstream instinct, though, is closer to raw SQL — you trade
> auto-generated queries for queries you can see and tune. Note also the
> explicit `context.Context` threaded through every call: that's Go's
> cancellation/deadline mechanism, roughly what Spring hides inside its
> transaction and request scopes.

Files: [OrderRepository.java](./spring-app/src/main/java/com/example/orders/domain/OrderRepository.java)
+ [Order.java](./spring-app/src/main/java/com/example/orders/domain/Order.java)
vs [repository.go](./go-app/internal/orders/repository.go).

### Transactions

Spring's `@Transactional` opens a transaction around the method. The Go app
leans on single atomic statements instead — e.g. `ClaimForProcessing` uses a
conditional `UPDATE ... WHERE status = 'PENDING'` that both transitions state
and guards against duplicate Kafka deliveries in one statement. For multi-step
transactions Go uses `pool.Begin(ctx)` / `tx.Commit(ctx)` explicitly.

---

## 6. Database migrations

**Spring:** Flyway is auto-detected; `V1__*.sql` files on the classpath run at
startup and are tracked in `flyway_schema_history`.

**Go:** `golang-migrate` is the direct equivalent. Migrations are embedded in
the binary with `//go:embed` and applied at startup:

```go
//go:embed migrations/*.sql
var migrations embed.FS

db.Migrate(cfg.DatabaseURL, migrations)   // applies 0001_*.up.sql, ...
```

`//go:embed` compiles the SQL files into the binary, so the deployable is a
single self-contained executable — no classpath, no external files. Files:
[V1__create_orders.sql](./spring-app/src/main/resources/db/migration/V1__create_orders.sql)
vs [migrations/](./go-app/migrations/) + [db.go](./go-app/internal/db/db.go).

---

## 7. Inbound security (OAuth2 resource server)

**Spring:** One property configures the whole resource server; a
`SecurityFilterChain` bean maps scopes to routes. Spring fetches the JWKS,
validates signature/issuer/expiry, and exposes scopes as `SCOPE_*` authorities.

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8090/realms/demo
```
```java
.requestMatchers(GET, "/api/orders/**").hasAuthority("SCOPE_orders:read")
.requestMatchers(POST, "/api/orders/**").hasAuthority("SCOPE_orders:write")
.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
```

**Go:** About 100 lines you own ([auth.go](./go-app/internal/auth/auth.go)).
`keyfunc` fetches and background-refreshes the JWKS; `golang-jwt` validates the
token; a middleware checks the `scope` claim and wraps the handler.

```go
token, err := jwt.Parse(tokenString, v.keys.Keyfunc,
    jwt.WithIssuer(v.issuer),
    jwt.WithExpirationRequired(),
    jwt.WithValidMethods([]string{"RS256"}))
// ...
if !hasScope(claims, scope) { forbidden(w, ...) ; return }
```

The route→scope table lives in the mux setup, exactly where Spring's
`authorizeHttpRequests` block does its job — the difference is you can read
every step of the validation. Files:
[SecurityConfig.java](./spring-app/src/main/java/com/example/orders/security/SecurityConfig.java)
vs [auth.go](./go-app/internal/auth/auth.go).

---

## 8. Outbound security (OAuth2 client credentials × 2)

This is the trickiest feature to get right in both stacks, and the clearest
illustration of the philosophies.

**Spring:** Declare two client registrations in YAML. An
`OAuth2AuthorizedClientManager` + a `RestClient` request interceptor fetch a
token per registration, cache it until expiry, and attach it as a bearer
header. Your client code never sees a token.

```yaml
registration:
  payment:   { provider: keycloak, client-id: payment-client,   authorization-grant-type: client_credentials, scope: payments:charge }
  inventory: { provider: keycloak, client-id: inventory-client, authorization-grant-type: client_credentials, scope: inventory:reserve }
```

> One caveat the code documents: the default authorized-client manager is
> request-scoped, but order processing runs on Kafka listener threads with no
> HTTP request in scope. The app swaps in the
> `AuthorizedClientServiceOAuth2AuthorizedClientManager` so tokens can be
> acquired off-request. See
> [OAuth2ClientConfig.java](./spring-app/src/main/java/com/example/orders/clients/OAuth2ClientConfig.java).

**Go:** `golang.org/x/oauth2/clientcredentials` does the same token
fetch-and-cache and hands back an `*http.Client` that injects the bearer header.
Two configs → two clients:

```go
cfg := clientcredentials.Config{
    ClientID: clientID, ClientSecret: clientSecret,
    TokenURL: tokenURL, Scopes: scopes,
}
httpClient := cfg.Client(ctx)   // transparently adds + refreshes the token
```

Each of `PaymentClient` and `InventoryClient` wraps its own token-injecting
client, so they use independent credentials and scopes — the same outcome as
two Spring registrations, with the token lifecycle visible in one small
package. Files:
[OAuth2ClientConfig.java](./spring-app/src/main/java/com/example/orders/clients/OAuth2ClientConfig.java)
+ [PaymentClient.java](./spring-app/src/main/java/com/example/orders/clients/PaymentClient.java)
vs [clients.go](./go-app/internal/clients/clients.go).

---

## 9. Messaging (Kafka)

**Spring:** `KafkaTemplate` for producing; `@KafkaListener` for consuming.
Spring runs the poll loop, deserializes JSON, commits offsets, and handles
rebalancing. Producing is one line.

```java
kafkaTemplate.send(topic, orderId.toString(), event);

@KafkaListener(topics = "${app.kafka.orders-topic}")
public void onOrderEvent(OrderEvent event) { ... }
```

**Go:** `segmentio/kafka-go`. Producing is a `Writer.WriteMessages` call. For
consuming, **you own the poll loop** — the code that `@KafkaListener` generates
for you is right there in [messaging.go](./go-app/internal/messaging/messaging.go):

```go
for {
    msg, err := c.reader.ReadMessage(ctx)   // blocks; auto-commits offset for the group
    if err != nil { /* handle context cancellation / poison messages */ }
    var event OrderEvent
    json.Unmarshal(msg.Value, &event)
    c.processor.Process(ctx, event.OrderID)
}
```

The consumer runs as a goroutine started in `main()` (`go consumer.Run(ctx)`).
Because both services use the same JSON shape and topic, they interoperate —
an order created by the Spring app is consumed by the Go app and vice versa.
(Spring's JSON serializer normally stamps a `__TypeId__` header; the config
turns that off with `spring.json.use.type.headers: false` so the Go producer's
plain JSON deserializes cleanly.) Files:
[OrderEventPublisher.java](./spring-app/src/main/java/com/example/orders/messaging/OrderEventPublisher.java)
+ [OrderEventConsumer.java](./spring-app/src/main/java/com/example/orders/messaging/OrderEventConsumer.java)
vs [messaging.go](./go-app/internal/messaging/messaging.go).

---

## 10. Scheduled work

**Spring:** `@EnableScheduling` + `@Scheduled(fixedDelayString = "...")`. The
framework owns a scheduler thread pool.

```java
@Scheduled(fixedDelayString = "${app.reporting.interval:30s}")
public void reportPendingOrders() { ... }
```

**Go:** A goroutine with a `time.Ticker`, started in `main()`. The scheduling,
the loop, and — crucially — the shutdown are all visible:

```go
func (r *PendingOrdersReporter) Run(ctx context.Context) {
    ticker := time.NewTicker(r.interval)
    defer ticker.Stop()
    for {
        select {
        case <-ctx.Done():   // graceful shutdown
            return
        case <-ticker.C:
            count, _ := r.repo.CountUnfinished(ctx)
            pendingOrders.Set(float64(count))
        }
    }
}
```

Files: [PendingOrdersReporter.java](./spring-app/src/main/java/com/example/orders/config/PendingOrdersReporter.java)
vs [reporter.go](./go-app/internal/jobs/reporter.go).

---

## 11. Configuration

**Spring:** `application.yaml` + `@ConfigurationProperties` records bound and
validated at startup, with profile support and relaxed binding.

**Go:** A `Config` struct populated from environment variables with explicit
defaults, loaded once in `main()`. No profiles, no relaxed binding — just a
function.

```go
func Load() (Config, error) {
    return Config{
        Port:        getenv("PORT", "8081"),
        DatabaseURL: getenv("DATABASE_URL", "postgres://orders:orders@localhost:5432/orders_go"),
        // ...
    }, nil
}
```

Files: [application.yaml](./spring-app/src/main/resources/application.yaml) +
the `config/` package vs [config.go](./go-app/internal/config/config.go).

---

## 12. Observability

**Spring:** `spring-boot-starter-actuator` exposes `/actuator/health`,
`/actuator/info`, `/actuator/metrics`, and (with the Micrometer Prometheus
registry) `/actuator/prometheus` — for free.

**Go:** Health is a handler that returns `{"status":"UP"}`; readiness pings the
DB pool; metrics come from the Prometheus client library's
`promhttp.Handler()`. Custom metrics are registered explicitly:

```go
var pendingOrders = promauto.NewGauge(prometheus.GaugeOpts{
    Name: "orders_pending", Help: "Orders not yet completed or failed",
})
mux.Handle("GET /metrics", promhttp.Handler())
```

Both apps expose an `orders_pending` gauge you can scrape. Files: the
`management` block in [application.yaml](./spring-app/src/main/resources/application.yaml)
+ [PendingOrdersReporter.java](./spring-app/src/main/java/com/example/orders/config/PendingOrdersReporter.java)
vs [httpapi.go](./go-app/internal/httpapi/httpapi.go) + [reporter.go](./go-app/internal/jobs/reporter.go).

---

## 13. Lifecycle & graceful shutdown

**Spring:** The container manages startup ordering and graceful shutdown
(`server.shutdown=graceful`) — mostly invisible.

**Go:** You own it, and it's explicit. `signal.NotifyContext` turns
SIGINT/SIGTERM into context cancellation; every long-running component
(HTTP server, Kafka consumer, reporter) hangs off that context; `defer` closes
the pool and Kafka writer:

```go
ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
// ... start server, consumer, reporter, all bound to ctx ...
<-ctx.Done()
server.Shutdown(shutdownCtx)   // drain in-flight requests, 10s budget
```

See [main.go](./go-app/main.go). This is arguably where Go's explicitness pays
off most: the entire lifecycle is one readable function.

---

## Summary: the trade-off in one table

| Dimension | Spring Boot | Go |
|---|---|---|
| Wiring | Component scan + DI container | `func main()` calls constructors |
| Lines of code | Fewer; behavior in annotations/starters | More; behavior in visible code |
| Learning curve | Know the framework's conventions | Know the language + a few libraries |
| "Where does X happen?" | Somewhere in auto-config | On a line you wrote |
| Startup | Reflection, classpath scanning | Compiled, direct — starts in ms |
| Deployable | JAR + JVM | Single static binary |
| Testing | Context slices, mocks, `@MockBean` | Plain structs + consumer-side interfaces |
| Failure modes | Misconfiguration, bean conflicts | Boilerplate, easy-to-forget steps |

Neither column is the "right answer." Spring's leverage is real: this service
is meaningfully less code in Java, and the starters encode years of hard-won
defaults. Go's leverage is also real: there's no framework to learn or fight,
the binary starts instantly, and every behavior is on a line you can read. The
goal of this project is to make the mapping concrete enough that you can move
between them deliberately.
