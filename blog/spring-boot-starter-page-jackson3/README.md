# spring-boot-starter-page-jackson3 Demo

This project demonstrates the problem that [`spring-boot-starter-page-jackson3`](https://github.com/StevenPG/spring-boot-starter-page-jackson3) solves: **Jackson 3 cannot deserialize Spring Data `Page<T>` responses out of the box.**

The demo is three Spring Boot 4 applications that you run together:

| Module | Port | Purpose |
|---|---|---|
| `page-server` | `8080` | Serves paginated `Page<User>` responses |
| `page-client-success` | `8081` | Consumes the server **with** the library — succeeds |
| `page-client-failure` | `8082` | Consumes the server **without** the library — fails |

---

## The Problem

Spring Data's built-in `PageModule` only handles **serialization**. When you call a REST API that returns a `Page<T>` and try to deserialize the response, Jackson 3 throws:

```
Type definition error: [simple type, class org.springframework.data.domain.Page]
```

This happens because `Page` is an interface (Jackson can't instantiate it) and `PageImpl` has no `@JsonCreator` constructor.

## The Fix

Add one dependency:

```groovy
implementation 'com.stevenpg:spring-boot-starter-page-jackson3:0.0.1'
```

Zero configuration required. Auto-configuration activates automatically.

---

## Requirements

- Java 17+
- Gradle 9+ (or use `./gradlew` if you have generated the wrapper)

Generate the Gradle wrapper if needed:

```bash
gradle wrapper --gradle-version 9.4.0
```

---

## Running the Demo

Open **three terminals** and start each application in order.

### 1. Start the server

```bash
cd page-server
../gradlew bootRun
```

The server starts on **port 8080**. You can verify it works directly:

```bash
curl "http://localhost:8080/api/users?page=0&size=3"
```

### 2. Start the success client

```bash
cd page-client-success
../gradlew bootRun
```

Starts on **port 8081**. Call it:

```bash
curl "http://localhost:8081/call-server?page=0&size=3"
```

Expected response:

```json
{
  "status": "SUCCESS",
  "message": "Page<User> deserialized with spring-boot-starter-page-jackson3",
  "page": 0,
  "size": 3,
  "totalElements": 10,
  "totalPages": 4,
  "first": true,
  "last": false,
  "content": [
    { "id": 1, "name": "Alice", "email": "alice@example.com" },
    { "id": 2, "name": "Bob",   "email": "bob@example.com" },
    { "id": 3, "name": "Charlie", "email": "charlie@example.com" }
  ]
}
```

### 3. Start the failure client

```bash
cd page-client-failure
../gradlew bootRun
```

Starts on **port 8082**. Call it:

```bash
curl "http://localhost:8082/call-server?page=0&size=3"
```

Expected response:

```json
{
  "status": "FAILURE",
  "error": "...",
  "rootCause": "tools.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `org.springframework.data.domain.Page`...",
  "fix": "Add 'com.stevenpg:spring-boot-starter-page-jackson3:0.0.1' to your dependencies!",
  "library": "https://github.com/StevenPG/spring-boot-starter-page-jackson3"
}
```

---

## Project Structure

```
.
├── page-server/                  # REST API — serves Page<User>
│   └── src/main/java/com/stevenpg/pageserver/
│       ├── PageServerApplication.java
│       ├── User.java
│       └── UserController.java
│
├── page-client-success/          # Client WITH the library — deserialization works
│   └── src/main/java/com/stevenpg/pageclientsuccess/
│       ├── PageClientSuccessApplication.java
│       ├── User.java
│       ├── ClientConfig.java
│       └── DemoController.java
│
└── page-client-failure/          # Client WITHOUT the library — deserialization fails
    └── src/main/java/com/stevenpg/pageclientfailure/
        ├── PageClientFailureApplication.java
        ├── User.java
        ├── ClientConfig.java
        └── DemoController.java
```

---

## How the Library Works

1. `PageJackson3AutoConfiguration` registers a `JsonMapperBuilderCustomizer` bean.
2. The customizer adds a `@JsonDeserialize(as = RestPage.class)` mixin on `Page.class`.
3. `RestPage<T>` extends `PageImpl<T>` with a null-safe `@JsonCreator` constructor that handles the standard Spring Data pagination envelope.

The only difference between `page-client-success` and `page-client-failure` is this single dependency in `build.gradle`:

```groovy
// page-client-success/build.gradle — has this line
implementation 'com.stevenpg:spring-boot-starter-page-jackson3:0.0.1'

// page-client-failure/build.gradle — does NOT have this line
```

---

## Related

- [spring-boot-starter-page-jackson3](https://github.com/StevenPG/spring-boot-starter-page-jackson3) — the library
- [Maven Central](https://central.sonatype.com/artifact/com.stevenpg/spring-boot-starter-page-jackson3)
