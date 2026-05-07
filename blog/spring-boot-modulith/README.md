# Spring Boot Modulith — E-Commerce Demo

A production-style reference project demonstrating **Spring Modulith** best practices on top of Spring Boot 3.4 and Java 21.

The domain is a simplified e-commerce backend with four business modules (Catalog, Orders, Inventory, Payments) that communicate exclusively through domain events and well-defined public APIs — never through each other's internal implementation details.

---

## What is Spring Modulith?

Spring Modulith is a framework that brings **enforced modularity** to a Spring Boot monolith.  Instead of splitting code into separate deployable services (microservices), it lets you define logical modules inside a single application and then:

- **Verify** at test time that no module reaches into another module's internals.
- **Communicate** between modules through application events (with transactional outbox guarantees) rather than direct method calls wherever possible.
- **Test** each module in isolation, loading only the slice of the application context that module needs.
- **Document** the architecture automatically as PlantUML diagrams and AsciiDoc pages.

The result is a codebase with the development simplicity of a monolith and the logical isolation of microservices — without the distributed systems overhead.

---

## Architecture

### Module dependency graph

```
catalog         (leaf — no external dependencies)
    ↑
orders          → catalog          (reads product details when creating an order)
    ↑
inventory       → orders           (listens for OrderPlacedEvent)
    ↑
payments        → inventory        (listens for StockReservedEvent)
payments        → orders           (calls OrderManagement directly to update order status)
```

The graph is **acyclic**.  This is enforced by `ModularityTests.verifyNoIllegalCrossModuleAccess()` on every build.

### Module layout convention

Every module follows the same two-layer layout:

```
<module>/
├── PublicType.java        ← accessible by other modules
├── AnotherPublicType.java
└── internal/
    ├── SomeRepository.java   ← invisible to other modules (enforced by Modulith)
    └── EventListener.java
```

Types in `<module>` root are the public API.  Types in `<module>/internal` are implementation details.  Spring Modulith's `verify()` check fails the build if any class in module A imports from `B.internal`.

### Event-driven workflow

```
POST /api/orders
  └─▶ OrderManagement.placeOrder()
        publishes ── OrderPlacedEvent ──▶ inventory/internal/OrderPlacedListener
                                               │
                         ┌─────────────────────┴──────────────────────┐
                         ▼ (stock ok)                                  ▼ (stock shortage)
               publishes StockReservedEvent                 publishes StockShortageEvent
                         │                                            │
                         ▼                                            ▼
          payments/internal/StockReservedListener            (order stays PENDING,
                    │                                          no payment attempted)
                    ▼
           PaymentService.process()
                    │
          ┌─────────┴────────────┐
          ▼ (approved)           ▼ (declined: amount ends in .99)
  orderManagement           orderManagement
    .confirm()                .markPaymentFailed()
  publishes                 publishes
  PaymentCompletedEvent     PaymentFailedEvent
```

All listeners are annotated with `@ApplicationModuleListener`.  This is a Spring Modulith composite annotation that wraps `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Transactional(propagation = REQUIRES_NEW)`, so:

1. The listener only fires after the publishing transaction commits (no phantom reads).
2. The listener runs in its own fresh transaction.
3. The event publication is recorded in the `event_publication` table **inside the same transaction** as the business write.  If the listener crashes, the platform retries it on the next startup (`republish-outstanding-events-on-restart: true`).

---

## Project structure

```
src/main/java/com/example/ecommerce/
├── EcommerceApplication.java
│
├── catalog/
│   ├── package-info.java          @ApplicationModule(displayName = "Catalog", allowedDependencies = {})
│   ├── Product.java               JPA entity — public aggregate root
│   ├── CatalogService.java        Public read-only service
│   ├── CatalogController.java     GET /api/catalog/products
│   └── internal/
│       └── ProductRepository.java Spring Data JPA — module-private
│
├── orders/
│   ├── package-info.java          @ApplicationModule(displayName = "Orders", allowedDependencies = {"catalog"})
│   ├── Order.java                 JPA aggregate root
│   ├── OrderItem.java             JPA entity (line item snapshot — price locked at order time)
│   ├── OrderStatus.java           PENDING | CONFIRMED | PAYMENT_FAILED | CANCELLED
│   ├── OrderPlacedEvent.java      Domain event — triggers Inventory and Payments
│   ├── PlaceOrderCommand.java     Validated input record
│   ├── OrderManagement.java       Public service — place / confirm / cancel orders
│   ├── OrderController.java       POST /api/orders, GET /api/orders/{id}, DELETE /api/orders/{id}
│   └── internal/
│       └── OrderRepository.java
│
├── inventory/
│   ├── package-info.java          @ApplicationModule(displayName = "Inventory", allowedDependencies = {"orders"})
│   ├── InventoryItem.java         JPA entity — stock per product
│   ├── StockReservedEvent.java    Published when all line items reserved successfully
│   ├── StockShortageEvent.java    Published when at least one item is out of stock
│   ├── InventoryService.java      Public service — query stock levels
│   ├── InventoryController.java   GET /api/inventory
│   └── internal/
│       ├── InventoryItemRepository.java
│       └── OrderPlacedListener.java     Reserves stock; publishes StockReserved/StockShortage
│
└── payments/
    ├── package-info.java          @ApplicationModule(displayName = "Payments", allowedDependencies = {"inventory","orders"})
    ├── Payment.java               JPA entity
    ├── PaymentStatus.java         PENDING | COMPLETED | FAILED
    ├── PaymentCompletedEvent.java
    ├── PaymentFailedEvent.java
    ├── PaymentService.java        Simulated gateway + calls OrderManagement to update status
    └── internal/
        ├── PaymentRepository.java
        └── StockReservedListener.java   Triggers PaymentService on successful stock reservation
```

---

## Tech stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Framework | 7.0 |
| Spring Modulith | 2.0.5 |
| Build tool | Gradle 8.14 (Kotlin DSL) |
| Database (runtime) | H2 in-memory |
| Metrics | Micrometer + Prometheus |
| Dashboards | Grafana |
| API docs | springdoc-openapi 3.x (Swagger UI) |

---

## Getting started

### Prerequisites

- JDK 21+
- Docker (only needed for Prometheus / Grafana observability stack)

### Run the application

```bash
./gradlew bootRun
```

The app starts on **http://localhost:8080** with an H2 in-memory database pre-seeded with four products and matching inventory records.

### Start the observability stack (optional)

```bash
docker compose up -d
```

This starts:

| Service | URL | Credentials |
|---|---|---|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

Prometheus scrapes `/actuator/prometheus` every 15 seconds.

---

## Available endpoints

### REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/catalog/products` | List all products |
| `GET` | `/api/catalog/products/{id}` | Get product by ID |
| `POST` | `/api/orders` | Place a new order |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `DELETE` | `/api/orders/{id}` | Cancel an order |
| `GET` | `/api/inventory` | List stock levels |
| `GET` | `/api/inventory/product/{productId}` | Stock for a specific product |

### Developer tools

| Tool | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| H2 Console | http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:ecommerce`) |
| Actuator health | http://localhost:8080/actuator/health |
| Module graph | http://localhost:8080/actuator/modulith |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |

### Try the full order flow

```bash
# 1. Find a product (use the seeded Laptop Pro 15)
PRODUCT_ID="11111111-0000-0000-0000-000000000001"

# 2. Place an order (successful — price is $1499.00, not ending in .99)
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "aaaaaaaa-0000-0000-0000-000000000001",
    "items": [{"productId": "'$PRODUCT_ID'", "quantity": 1}]
  }' | jq .

# 3. Poll the order until status becomes CONFIRMED
ORDER_ID="<id from step 2>"
curl -s http://localhost:8080/api/orders/$ORDER_ID | jq .status

# 4. Try a payment that will be declined (USB-C Hub at $49.99 — ends in .99)
HUB_ID="11111111-0000-0000-0000-000000000003"
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "aaaaaaaa-0000-0000-0000-000000000002",
    "items": [{"productId": "'$HUB_ID'", "quantity": 1}]
  }' | jq .
# → order will land in PAYMENT_FAILED status
```

---

## Running the tests

```bash
./gradlew test
```

40 tests across seven test classes:

| Class | Type | What it tests |
|---|---|---|
| `ModularityTests` | Static bytecode analysis | Module boundaries, dependency graph, event ownership, no cycles |
| `EcommerceApplicationTests` | `@SpringBootTest` | Full context loads without errors |
| `CatalogModuleTests` | `@ApplicationModuleTest` (STANDALONE) | Catalog module in isolation |
| `OrderModuleTests` | `@ApplicationModuleTest` (DIRECT_DEPENDENCIES) | Orders + catalog slice; `AssertablePublishedEvents` assertion |
| `InventoryModuleTests` | `@ApplicationModuleTest` (ALL_DEPENDENCIES) | Inventory + all transitive deps; event-driven reservation; Moments / `TimeMachine` |
| `OrderFlowScenarioTests` | `@SpringBootTest` + `@EnableScenarios` | Full async event chain; event-based and state-based `Scenario` assertions |

The `generateModuleDocumentation` test writes PlantUML diagrams and AsciiDoc pages to `build/spring-modulith-docs/`.

---

## Key Spring Modulith patterns demonstrated

### 1. Module boundary enforcement

```java
// package-info.java in each module root
@ApplicationModule(
    displayName = "Orders",
    allowedDependencies = {"catalog"}   // only catalog is allowed
)
package com.stevenpg.ecommerce.orders;
```

`ApplicationModules.of(EcommerceApplication.class).verify()` scans bytecode and fails with a detailed report if any class violates this declaration.

### 2. Transactional event publishing

```java
// OrderManagement.java
orders.save(order);                              // 1. persist
events.publishEvent(new OrderPlacedEvent(...));  // 2. record publication in event_publication table
                                                 //    (same transaction — atomic)
```

```java
// inventory/internal/OrderPlacedListener.java
@ApplicationModuleListener          // fires AFTER_COMMIT in a new transaction
void onOrderPlaced(OrderPlacedEvent event) { ... }
```

If the listener throws, the event stays in `event_publication` with no `completion_date` and is replayed on restart.

### 3. Isolated module testing with `AssertablePublishedEvents`

```java
@ApplicationModuleTest   // loads only the catalog module's beans
class CatalogModuleTests {
    @Autowired CatalogService catalog;
    ...
}

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class OrderModuleTests {
    // loads orders + catalog (direct dep) — not inventory or payments
    @Autowired OrderManagement orders;

    @Test
    void placeOrderPublishesOrderPlacedEvent(AssertablePublishedEvents events) {
        orders.placeOrder(command);

        assertThat(events)
            .contains(OrderPlacedEvent.class)
            .matching(e -> e.customerId().equals(command.customerId()));
    }
}
```

`AssertablePublishedEvents` is injected by Spring Modulith's JUnit 5 extension.  It captures every `ApplicationEvent` published on the current thread during the test, then exposes a fluent AssertJ API for assertions.

### 4. Full async flow testing with the `Scenario` API

```java
@SpringBootTest
@EnableScenarios   // registers ScenarioParameterResolver
class OrderFlowScenarioTests {

    @Test
    void successfulOrderFlowPublishesPaymentCompletedEvent(Scenario scenario) {
        scenario.stimulate(() -> orders.placeOrder(command))   // commits in REQUIRES_NEW
            .andWaitForEventOfType(PaymentCompletedEvent.class) // Awaitility poll (10 s)
            .toArriveAndVerify((event, order) ->
                assertThat(event.orderId()).isEqualTo(order.getId())
            );
    }
}
```

`Scenario` wraps the stimulus in its own transaction and then uses Awaitility to poll until the expected downstream event is published.  The second parameter to `toArriveAndVerify` is the return value of the stimulus (`Order` here), making it easy to cross-check the event against the entity that triggered the flow.

### 5. Dependency graph assertions in tests

```java
// ModularityTests.java
@Test
void catalogHasNoExternalModuleDependencies() {
    var deps = modules.getModuleByName("catalog").orElseThrow()
            .getDependencies(modules);
    assertThat(deps.isEmpty()).isTrue();
}

@Test
void ordersOnlyDependsOnCatalog() {
    List<String> depNames = modules.getModuleByName("orders").orElseThrow()
            .getDependencies(modules)
            .uniqueModules()
            .map(ApplicationModule::getName)
            .toList();
    assertThat(depNames).containsExactlyInAnyOrder("catalog");
}
```

### 6. Breaking the saga cycle

A naive choreography-based saga creates a circular dependency:

```
orders → payments (imports PaymentCompletedEvent)
payments → inventory (imports StockReservedEvent)
inventory → orders (imports OrderPlacedEvent)
─── CYCLE ───
```

This project breaks the cycle by having `PaymentService` call `OrderManagement` directly (both are in the `payments → orders` allowed dependency), removing the need for `orders` to import any type from `payments`:

```
catalog ← orders ← inventory ← payments → orders
                                          ↑ (direct call, no event)
```

### 7. Module lifecycle ordering with `ApplicationModuleInitializer`

Spring Modulith guarantees that module initializers run on `ApplicationReadyEvent` in dependency order — upstream modules always finish before downstream ones.

```java
// catalog/internal/CatalogInitializer.java
@Component
class CatalogInitializer implements ApplicationModuleInitializer {

    @Override
    public void initialize() {
        // catalog has no dependencies → always runs first
        // orders' initializer (if it had one) would run after this
        long count = products.count();
        if (count == 0) {
            log.warn("Catalog module initialized with no products — is the seed data loaded?");
        } else {
            log.info("Catalog module initialized with {} product(s) in the catalog", count);
        }
    }
}
```

This is preferable to `@PostConstruct` (which fires during bean creation in arbitrary order) or a bare `@EventListener(ApplicationReadyEvent.class)` (no ordering guarantee across modules).

### 8. Time-based domain events with Moments and `TimeMachine`

`spring-modulith-moments` publishes time-based events (`HourHasPassed`, `DayHasPassed`, `WeekHasPassed`, …) on a schedule.  Modules react with `@ApplicationModuleListener` exactly as they would to any other domain event.

```java
// inventory/internal/DailyInventoryAuditListener.java
@ApplicationModuleListener
void onDayHasPassed(DayHasPassed event) {
    repository.findAll().stream()
        .filter(item -> item.available() < LOW_STOCK_THRESHOLD)
        .forEach(item ->
            events.publishEvent(new LowStockWarningEvent(item.getProductId(), item.available()))
        );
}
```

In tests, activating `TimeMachine` (via `spring.modulith.moments.enable-time-machine: true`) lets you advance the clock programmatically instead of waiting for the real scheduler:

```yaml
# test/resources/application.yml
spring:
  modulith:
    moments:
      enable-time-machine: true
```

```java
// InventoryModuleTests.java
@Autowired TimeMachine timeMachine;

@Test
void lowStockWarningPublishedWhenInventoryBelowThreshold(Scenario scenario) {
    jdbc.update("UPDATE inventory_items SET quantity_on_hand = 3 WHERE product_id = ?", productId);

    // shiftBy(1 day) publishes DayHasPassed → DailyInventoryAuditListener fires
    scenario.stimulate(() -> timeMachine.shiftBy(Duration.ofDays(1)))
        .andWaitForEventOfType(LowStockWarningEvent.class)
        .toArriveAndVerify(warning -> {
            assertThat(warning.productId()).isEqualTo(productId);
            assertThat(warning.availableQty()).isLessThan(10);
        });
}
```

Wrapping `shiftBy()` in `scenario.stimulate()` is required so the `DayHasPassed` event is published inside a transaction, giving `@ApplicationModuleListener` (AFTER_COMMIT) a transaction boundary to react to.

### 9. `ALL_DEPENDENCIES` bootstrap mode

`@ApplicationModuleTest` supports three bootstrap modes that trade isolation for fidelity:

| Mode | Beans loaded |
|---|---|
| `STANDALONE` (default) | Only the module under test |
| `DIRECT_DEPENDENCIES` | Module + its immediate dependencies |
| `ALL_DEPENDENCIES` | Module + full transitive dependency chain |

`ALL_DEPENDENCIES` is useful when the module's async listeners depend on types from transitive upstream modules.  `InventoryModuleTests` uses it so `OrderPlacedListener` can resolve `OrderPlacedEvent` (from `orders`) and the `orders` module can resolve `Product` (from `catalog`):

```java
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
class InventoryModuleTests {
    // inventory + orders + catalog are all loaded
    // payments is NOT loaded — it has no transitive path to inventory
}
```

### 10. `andWaitForStateChange` — polling state instead of events

Use `andWaitForStateChange` when you want to assert on persisted state rather than which events fired.  Always supply an explicit acceptance `Predicate` — the default single-arg overload accepts any non-null value, including `0`, and will stop polling immediately for numeric state:

```java
// WRONG — stops polling as soon as the supplier returns any non-null value (including 0)
scenario.publish(event)
    .andWaitForStateChange(() -> inventory.findByProductId(productId)
        .map(InventoryItem::getQuantityReserved).orElse(0));

// CORRECT — explicit predicate ensures polling continues until the units are actually reserved
scenario.publish(event)
    .andWaitForStateChange(
        () -> inventory.findByProductId(productId).map(InventoryItem::getQuantityReserved).orElse(0),
        reserved -> reserved >= qty
    )
    .andVerify(reserved -> assertThat(reserved).isEqualTo(qty));
```

When the stimulus returns a value you need inside the polling supplier, capture it with an `AtomicReference`:

```java
var placed = new AtomicReference<Order>();

scenario.stimulate(() -> {
    var order = orders.placeOrder(command);
    placed.set(order);   // capture before the lambda returns
    return order;
})
.andWaitForStateChange(
    () -> orders.findById(placed.get().getId()).map(Order::getStatus).orElse(OrderStatus.PENDING),
    status -> status == OrderStatus.CONFIRMED
)
.andVerify(finalStatus -> assertThat(finalStatus).isEqualTo(OrderStatus.CONFIRMED));
```

---

## Extending the project

| Task | Where to start |
|---|---|
| Add a new module | Create `src/main/java/com/stevenpg/ecommerce/<name>/` with a `package-info.java` |
| Add a new cross-module event | Define the record in the publishing module's root package; consume it with `@ApplicationModuleListener` in the target module's `internal` package |
| Switch to PostgreSQL | Change the datasource URL and add `org.postgresql:postgresql` to dependencies; remove H2 |
| Externalize events to Kafka | Annotate events with `@Externalized("topic-name")` and add `spring-modulith-events-kafka` |
| Add an integration test for the full flow | Use `@SpringBootTest` and publish `OrderPlacedEvent` directly, asserting on final order status |
