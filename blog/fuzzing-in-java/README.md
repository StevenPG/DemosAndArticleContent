# Fuzzing in Java — a hands-on demo

A small Spring Boot REST API that teaches **fuzzing**: an automated testing technique
that throws large volumes of generated input at your code to find crashes, edge cases,
and security bugs that example-based tests miss.

This project ships with **three deliberately planted bugs**. Each one has a committed
crash input, so the moment you clone the repo and run the tests, three of them fail.
Your job is to fix the bugs and watch the suite go green — the same loop a fuzzer drives
in a real codebase.

- **Stack:** Java 25 · Gradle 9.5 · Spring Boot 4.0 · [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) 0.30
- **Fuzzer:** Jazzer — the de-facto standard coverage-guided fuzzer for the JVM, used by Google's OSS-Fuzz.

---

## Why fuzzing?

A hand-written test checks the inputs *you thought of*. A fuzzer checks the inputs you
*didn't* — empty strings, gigantic numbers, null bytes, malformed JSON, values one past a
boundary. It mutates inputs, watches which new code branches each mutation reaches
(coverage guidance), and keeps the inputs that explore deeper.

For a REST API, the attack surface is every path variable, query parameter, and request
body. That is exactly what this demo fuzzes.

---

## The API

All endpoints are stubs — they return canned data — so you can focus on the *inputs*, not
the business logic. Constraint validation (Jakarta Bean Validation) runs on every one.

| Method   | Path                       | REST pattern shown                         |
|----------|----------------------------|--------------------------------------------|
| `GET`    | `/api/users/{id}`          | Path variable + regex constraint           |
| `GET`    | `/api/users`               | Optional, typed query parameters           |
| `POST`   | `/api/users`               | Validated request body                     |
| `DELETE` | `/api/users/{id}`          | Delete by id                               |
| `GET`    | `/api/products`            | Rich query filtering + pagination          |
| `POST`   | `/api/products`            | Complex body with correlated fields        |
| `GET`    | `/api/products/{id}`       | Path variable lookup                       |
| `PUT`    | `/api/products/{id}`       | Partial update (path variable + body)      |
| `DELETE` | `/api/products/{id}`       | Delete by id                               |
| `POST`   | `/api/orders`              | Body with arithmetic-heavy logic           |
| `GET`    | `/api/orders/{orderId}`    | Path variable + repeatable `expand` option |
| `GET`    | `/api/orders`              | List with required + optional filters      |
| `DELETE` | `/api/orders/{orderId}`    | Cancel an order                            |

---

## Quick start

```bash
# Run the test suite — three fuzz tests fail on purpose.
./gradlew test
```

Expected result: **15 tests run, 3 fail.** Each failure is a pre-committed crash input
reproducing one planted bug:

```
OrderControllerFuzzTest   > fuzzCreateOrder(byte[])   > crash-int-overflow.json   FAILED
ProductControllerFuzzTest > fuzzCreateProduct(byte[]) > crash-metadata-npe.json   FAILED
UserControllerFuzzTest    > fuzzSearchByName(byte[])  > crash-short-name          FAILED
```

---

## The three planted bugs

Each bug passes constraint validation, then crashes in the controller body. The committed
crash input lives in the Jazzer **seed corpus**; a human-readable explanation lives in
[`src/main/resources/fuzzing-findings/`](src/main/resources/fuzzing-findings).

| # | Bug                              | Endpoint            | Seed corpus file                                                                          |
|---|----------------------------------|---------------------|-------------------------------------------------------------------------------------------|
| 1 | `StringIndexOutOfBoundsException`| `GET /api/users`    | `…/UserControllerFuzzTestInputs/fuzzSearchByName/crash-short-name`                         |
| 2 | `NullPointerException`           | `POST /api/products`| `…/ProductControllerFuzzTestInputs/fuzzCreateProduct/crash-metadata-npe.json`             |
| 3 | Integer overflow                 | `POST /api/orders`  | `…/OrderControllerFuzzTestInputs/fuzzCreateOrder/crash-int-overflow.json`                 |

**Bug 1 — short search term.** `UserController#searchUsers` builds a 3-character prefix
with `name.substring(0, 3)`. The code guards the `null` and empty cases but forgets that
1- and 2-character names are *also* too short. Input `"ab"` crashes it.

**Bug 2 — null map value.** `ProductController#createProduct` reads `metadata.get("category")`
and calls `.toUpperCase()` on it. Bean Validation never inspects the *values* inside a map,
so a body with `"metadata": {"category": null}` sails through validation and dereferences null.

**Bug 3 — integer overflow.** `OrderController#createOrder` computes
`int total = quantity * unitPrice`. With `quantity = unitPrice = 50000`, the true product
(2,500,000,000) overflows a 32-bit `int` and wraps to a negative number.

---

## Your task: fix the bugs

Open each controller, find the comment marked `BUG:`, and apply the fix described next to
it. After each fix, re-run `./gradlew test` and watch one more failure disappear. When all
three are fixed, the suite is green.

The fixes, in short:

```java
// Bug 1 — UserController#buildSearchPrefix
return (name.length() >= 3 ? name.substring(0, 3) : name).toLowerCase();

// Bug 2 — ProductController#resolveCategory
return Optional.ofNullable(request.metadata().get("category"))
        .map(String::toUpperCase).orElse("UNCATEGORIZED");

// Bug 3 — OrderController#createOrder
long total = (long) quantity * unitPrice;
```

---

## How the fuzz tests work

Fuzz tests are JUnit 5 methods annotated with Jazzer's `@FuzzTest`. They run in two modes.

### Regression mode (default — `./gradlew test`)

Jazzer replays the empty input plus every file in the test's **seed corpus** directory.
Each input becomes its own test invocation. This guarantees a bug, once found, can never
be silently reintroduced. This is the mode that fails on the three planted bugs.

The seed corpus convention is:

```
src/test/resources/<package>/<TestClassName>Inputs/<methodName>/
```

### Fuzzing mode (`JAZZER_FUZZ=1 ./gradlew test`)

Jazzer *generates* new inputs, guided by code coverage — mutating bytes, measuring which
new branches each mutation reaches, and keeping inputs that explore deeper. When it
triggers an unhandled exception it **automatically saves the crashing input** into the
seed corpus directory, so it instantly becomes a regression test. Commit that file and the
crash is locked in forever.

```bash
# Fuzz a single test (each @FuzzTest runs up to 5 minutes by default)
JAZZER_FUZZ=1 ./gradlew test --tests "*.ProductControllerFuzzTest.fuzzCreateProduct"
```

### Two ways to receive fuzzed input

- **`byte[] data`** — the raw bytes *are* the input. Perfect when the thing you fuzz is
  itself a byte stream: an HTTP request body, a single query value. Seed corpus files are
  literal and human-readable (just open `crash-metadata-npe.json`). Used by the three
  buggy tests.
- **`FuzzedDataProvider data`** — a cursor that hands out typed values (`consumeInt`,
  `consumeString`, …). Perfect for assembling structured, multi-field requests. Jazzer
  mutates the typed values intelligently. Used by the parameter-fuzzing tests.

### The invariant being tested

Every fuzz test asserts the same property: **no input should ever produce an HTTP 5xx.**

- `4xx` (bad request, not found, …) → an *expected* rejection. Fine.
- `5xx` → an *unexpected* crash. A bug. The fuzzer records the input.

`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so malformed requests,
unknown routes, and validation failures all return clean `4xx` responses — leaving `5xx`
to mean exactly one thing: your code threw an exception it didn't handle.

---

## Notes on versions

- The project compiles to and runs on **Java 25**. Jazzer 0.30 instruments Java 25 class
  files (bytecode major version 69) without issue — including JDK internals — so no
  bytecode-downgrade workaround is needed.
- Running the app: `./gradlew bootRun` starts it on port 8080.
