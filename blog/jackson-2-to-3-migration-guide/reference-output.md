# Jackson 2 to Jackson 3 Migration Guide — Reference Output

Captured on 2026-08-01 by running the project end to end:

```bash
cd jackson2-example && ./gradlew build     # BUILD SUCCESSFUL
cd ../jackson3-example && ./gradlew build  # BUILD SUCCESSFUL
./scripts/run-both.sh
./scripts/compare-requests.sh
./scripts/stop-both.sh
```

Environment: Linux x86_64, OpenJDK 21.0.10 (Temurin/Ubuntu build), Gradle wrapper 8.14,
no local Maven cache primed. Both apps built from a clean checkout with no edits.

## 1. Build and test

| Project | Gradle result | Tests | Time |
| --- | --- | --- | --- |
| `jackson2-example` | BUILD SUCCESSFUL in 1m 48s | 5 run, 0 failed, 0 skipped | 1.79s |
| `jackson3-example` | BUILD SUCCESSFUL in 2m 30s | 5 run, 0 failed, 0 skipped | 2.82s |

`jackson3-example` compiles with one deprecation note, on `StreamingWriter.java` — worth
knowing before you assume a clean migration means a warning-free one:

```
> Task :compileJava
Note: .../jackson3example/json/StreamingWriter.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
```

## 2. The dependency claim, verified from the fat jars

The README's headline gotcha is that `jackson-annotations` did **not** move to
`tools.jackson`. Here is the actual jar list from each `bootJar`, which is the
primary evidence:

```
$ unzip -l jackson2-example/build/libs/jackson2-example-0.0.1-SNAPSHOT.jar | grep jackson
jackson-annotations-2.21.jar          <-- annotations
jackson-core-2.21.4.jar
jackson-databind-2.21.4.jar
jackson-datatype-jdk8-2.21.4.jar      <-- separate module
jackson-datatype-jsr310-2.21.4.jar    <-- separate module
jackson-module-parameter-names-2.21.4.jar

$ unzip -l jackson3-example/build/libs/jackson3-example-0.0.1-SNAPSHOT.jar | grep jackson
jackson-annotations-2.21.jar          <-- SAME artifact, same 2.x coordinate
jackson-core-3.1.4.jar
jackson-databind-3.1.4.jar
spring-boot-jackson-4.1.0.jar
```

Three things fall out of that diff:

1. `jackson-annotations-2.21.jar` is byte-identical across both apps. The annotations
   really did stay at `com.fasterxml.jackson.core:jackson-annotations`.
2. `jackson-datatype-jsr310` and `jackson-datatype-jdk8` are simply gone in the Jackson 3
   app — `java.time` and `Optional` support is in `jackson-databind` now, and nothing
   registers those modules.
3. Spring Boot 4.1 pulls Jackson in through `spring-boot-jackson`, not
   `spring-boot-starter-json`.

## 3. Runtime startup

```
Starting Jackson2ExampleApplication v0.0.1-SNAPSHOT using Java 21.0.10
  Starting Servlet engine: [Apache Tomcat/10.1.55]
  Tomcat started on port 8082 (http) with context path '/'
  Started Jackson2ExampleApplication in 6.705 seconds (process running for 8.008)

Starting Jackson3ExampleApplication v0.0.1-SNAPSHOT using Java 21.0.10
  Starting Servlet engine: [Apache Tomcat/11.0.22]
  Tomcat started on port 8083 (http) with context path '/'
  Started Jackson3ExampleApplication in 6.047 seconds (process running for 7.544)
```

(Boot 3.5 brings Tomcat 10.1, Boot 4.1 brings Tomcat 11 — unrelated to Jackson, but it is
the other thing that changes when you move the Boot line.)

## 4. `./scripts/compare-requests.sh` — full output

The point of the script is that the left and right columns are identical. They are.

### 4.1 `GET /api/posts/sample`

jackson2-example (Spring Boot 3.5 / Jackson 2.21):

```json
{
  "title": "Jackson 2 to 3, a Field Guide",
  "author": {
    "name": "Steven Gantz",
    "twitterHandle": "@stevenpg"
  },
  "status": "PUBLISHED",
  "publishedAt": "2026-01-15T10:30:00Z",
  "tags": [
    "jackson",
    "migration",
    "spring-boot"
  ],
  "sponsorshipFee": "49.99 USD"
}
```

jackson3-example (Spring Boot 4.1 / Jackson 3.1):

```json
{
  "title": "Jackson 2 to 3, a Field Guide",
  "author": {
    "name": "Steven Gantz",
    "twitterHandle": "@stevenpg"
  },
  "status": "PUBLISHED",
  "publishedAt": "2026-01-15T10:30:00Z",
  "tags": [
    "jackson",
    "migration",
    "spring-boot"
  ],
  "sponsorshipFee": "49.99 USD"
}
```

`Optional<String>` (the `twitterHandle`), `Instant` (`publishedAt`, rendered as ISO-8601
rather than an epoch decimal), and the custom `Money` serializer all agree — the Jackson 3
side just needed two fewer modules registered to get there.

### 4.2 `POST /api/comments/echo` (record DTO round trip)

Request body, sent to both:

```json
{"author":"reader42","body":"Great migration guide!"}
```

Response, identical from both:

```json
{
  "author": "reader42",
  "body": "Great migration guide!"
}
```

### 4.3 `GET /api/health/streamed` (streaming API)

Both apps, same output (epoch second differs per run):

```json
{
  "service": "jackson2-example",
  "healthy": true,
  "checkedAtEpochSeconds": 1785615793
}
```

```json
{
  "service": "jackson3-example",
  "healthy": true,
  "checkedAtEpochSeconds": 1785615793
}
```

### 4.4 `GET /api/posts/sample/enriched` (tree model)

Both apps append the same two fields to the same document:

```json
{
  "title": "Jackson 2 to 3, a Field Guide",
  "author": {
    "name": "Steven Gantz",
    "twitterHandle": "@stevenpg"
  },
  "status": "PUBLISHED",
  "publishedAt": "2026-01-15T10:30:00Z",
  "tags": [
    "jackson",
    "migration",
    "spring-boot"
  ],
  "sponsorshipFee": "49.99 USD",
  "wordCountEstimate": 7,
  "enrichedBy": "TreeModelEnricher"
}
```

### 4.5 `GET /api/posts/broken/raw-json` (checked vs. unchecked)

jackson2-example — `JsonProcessingException` is checked, so the controller was forced to
declare or catch it:

```json
{
  "exceptionType": "InvalidDefinitionException",
  "message": "Direct self-reference leading to cycle"
}
```

`HTTP status: 500`

jackson3-example — `JacksonException` is unchecked, so nothing in the signature says so:

```json
{
  "exceptionType": "InvalidDefinitionException",
  "message": "Direct self-reference leading to cycle"
}
```

`HTTP status: 500`

Same exception simple name, same message, same status code. The only difference is
whether the compiler made `BlogPostController` say it could happen — which is exactly the
migration hazard: existing `catch (JsonProcessingException e)` blocks keep compiling
against Jackson 2 semantics and quietly stop catching anything once the hierarchy becomes
unchecked.

## 5. One defect found

`stop-both.sh` printed "Stopped jackson2-example (pid ...)" while both apps kept serving.
`run-both.sh` started each app as

```bash
(cd "$ROOT_DIR/jackson2-example" && nohup java -jar ... > log 2>&1 & echo $! > pidfile)
```

where the `&` binds to the whole `cd && nohup java` list, so `$!` is the subshell's pid and
the JVM is its child. Killing the recorded pid left the JVM holding port 8082, and a second
`run-both.sh` would have failed with "port already in use". The scripts now background the
subshell and `exec` into `java`, which keeps one pid from the pid file down to the JVM.
Re-verified: `stop-both.sh` frees both ports, and both apps disappear from the process table.

## 6. Verification summary

| Check | Result |
| --- | --- |
| `jackson2-example` compiles | pass |
| `jackson3-example` compiles | pass |
| `jackson2-example` tests (5) | pass |
| `jackson3-example` tests (5) | pass |
| `run-both.sh` starts both apps | pass |
| `compare-requests.sh` all 5 scenarios | pass, output identical across versions |
| `stop-both.sh` tears both down | pass, after the pid fix in section 5 |
| Annotations artifact unchanged across versions | confirmed from both fat jars |
