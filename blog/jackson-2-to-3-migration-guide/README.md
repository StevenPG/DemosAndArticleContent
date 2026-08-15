# Jackson 2 to Jackson 3 Migration Guide — Companion Project

Two intentionally parallel Spring Boot applications - a "before" and an
"after" - that exercise the same JSON features so every difference between
Jackson 2 (`com.fasterxml.jackson`) and Jackson 3 (`tools.jackson`) is visible
as a diff, not an abstract claim.

| Project | Spring Boot | Jackson | Port |
|---|---|---|---|
| `jackson2-example` | 3.5.16 | 2.21 (`com.fasterxml.jackson`) | 8082 |
| `jackson3-example` | 4.1.0 | 3.1 (`tools.jackson`) | 8083 |

Every class in `jackson3-example` has an identically-named sibling in
`jackson2-example` with the same package suffix. Open them side by side -
that pairing IS the migration guide. Every non-trivial diff is called out in
a `/** Migration note: ... */` Javadoc comment at the exact line it applies to.

```bash
./scripts/run-both.sh         # builds and starts both apps
./scripts/compare-requests.sh # fires identical requests at both, side by side
./scripts/stop-both.sh        # tears both down
```

---

## Why this migration is not just a package rename

Jackson 3.0 (GA late 2025) is the first major version bump since Jackson 2.0
(2013). Unlike most Jackson releases, it changes Maven coordinates and Java
package names - `com.fasterxml.jackson.*` becomes `tools.jackson.*` for most
of the project - which is why "migration guide" is the right framing instead
of "upgrade guide". This project verified every claim below directly against
the Jackson 3.2.0 and Jackson 2.21.4 jars (not from memory), so treat it as a
primary source.

### 1. Package and Maven coordinates move - except one that doesn't

| Artifact | Jackson 2 | Jackson 3 |
|---|---|---|
| Core streaming | `com.fasterxml.jackson.core:jackson-core` | `tools.jackson.core:jackson-core` |
| Data-binding | `com.fasterxml.jackson.core:jackson-databind` | `tools.jackson.core:jackson-databind` |
| BOM | `com.fasterxml.jackson:jackson-bom` | `tools.jackson:jackson-bom` |
| **Annotations** | `com.fasterxml.jackson.core:jackson-annotations` | **same** - `com.fasterxml.jackson.core:jackson-annotations` |

**The single most important gotcha in this whole migration:** `jackson-annotations`
did NOT move. `@JsonProperty`, `@JsonCreator`, `@JsonInclude`, `@JsonIgnore`,
every annotation you use, stays at `com.fasterxml.jackson.annotation` in
BOTH versions - confirmed straight from the `jackson-databind` 3.2.0 POM,
which pins `com.fasterxml.jackson.core:jackson-annotations` as a dependency
with the comment *"Annotations remain at Jackson 2.x group id."* Compare
`jackson2-example`'s and `jackson3-example`'s `Author.java` / `BlogPost.java` -
the annotation imports are byte-for-byte identical.

Package rename inside the classes that DID move: `com.fasterxml.jackson.databind.ObjectMapper`
becomes `tools.jackson.databind.ObjectMapper`; `com.fasterxml.jackson.core.JsonParser`
becomes `tools.jackson.core.JsonParser`. Mechanical, but touches every file.

### 2. `ObjectMapper` becomes immutable

Verified directly against both jars' public API surface:

* **Jackson 2:** `ObjectMapper` has ~30 instance mutator methods -
  `registerModule(...)`, `configure(...)`, `enable(...)`, `disable(...)` -
  every one of them mutates the SAME instance in place and remains callable
  for the object's entire lifetime, even after it's handed out to callers.
* **Jackson 3:** `ObjectMapper` has **none** of those methods. The only way
  to configure one is `JsonMapper.builder()...build()`; the resulting mapper
  is immutable forever. Need different settings? `mapper.rebuild()...build()`
  gives you a brand-new mapper - the original is untouched.

See `ObjectMapperConfig.java` in both projects for the same configuration
expressed the mutable way and the builder way.

### 3. Exceptions: checked becomes unchecked (with a name collision to watch for)

* **Jackson 2:** `com.fasterxml.jackson.core.JacksonException extends IOException`
  (checked). `JsonProcessingException extends JacksonException`. Every
  `ObjectMapper.writeValueAsString(...)` / `readValue(...)` call either
  declares `throws JsonProcessingException` or gets wrapped in a try/catch.
* **Jackson 3:** `tools.jackson.core.JacksonException extends RuntimeException`
  (unchecked). Same simple class name, completely different hierarchy,
  distinguished only by package. Nothing forces a caller to declare or catch
  it anymore.

See `BlogPostController.sampleAsRawJson()` in both projects: identical body,
one has a `throws` clause and one doesn't. `JacksonErrorAdvice` in both
projects still exists and still matters - unchecked exceptions still need
somewhere to land before they become a bare 500.

### 4. Three modules disappear into jackson-databind

Confirmed from the `jackson-bom` 3.2.0 POM, which lists these as
*"Merged into jackson-databind for Jackson 3.0"*:

| Jackson 2 (separate module + registration required) | Jackson 3 |
|---|---|
| `jackson-datatype-jdk8` + `mapper.registerModule(new Jdk8Module())` | built into jackson-databind - `Optional<T>` just works |
| `jackson-module-parameter-names` | built into jackson-databind |
| `jackson-datatype-jsr310` + `mapper.registerModule(new JavaTimeModule())` | built into jackson-databind (package `tools.jackson.databind.ext.javatime`) - `Instant`/`LocalDate`/etc. just work |

See `Author.twitterHandle` (`Optional<String>`) and `BlogPost.publishedAt`
(`Instant`) in both projects, and compare each project's `ObjectMapperConfig` -
jackson2-example registers two extra modules that jackson3-example doesn't
need at all.

### 5. A few renames worth knowing by name

| Jackson 2 | Jackson 3 | Why |
|---|---|---|
| `Module` | `JacksonModule` | stopped colliding with `java.lang.Module` (JPMS) |
| `JsonSerializer<T>` | `ValueSerializer<T>` | databind isn't JSON-only in spirit anymore (CBOR, Smile, ...) |
| `JsonDeserializer<T>` | `ValueDeserializer<T>` | same reason |
| `SerializerProvider` | `SerializationContext` | `DeserializationContext` keeps its name - only the serialize-side context was renamed |
| `JsonGenerator.writeStringField/writeBooleanField/writeNumberField` | `writeStringProperty/writeBooleanProperty/writeNumberProperty` | "field" implied JSON objects; "property" applies to every backend format |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` | `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` | date/time toggles moved off the catch-all feature enum onto a java.time-specific one |

See `MoneySerializer.java` / `MoneyDeserializer.java` / `MoneyModule.java` in
both projects for the serializer renames, and `StreamingWriter.java` for the
streaming API rename.

### 6. A default-behavior change: empty beans no longer fail

Verified by running both versions against the same empty class: Jackson 2's
`FAIL_ON_EMPTY_BEANS` (enabled by default) throws `InvalidDefinitionException`
when asked to serialize a type with zero discoverable properties. **Jackson 3
serializes it as `{}` instead** - the same feature flag still exists, but the
default flipped. Self-reference detection (`FAIL_ON_SELF_REFERENCES`) did
NOT change - both versions still refuse to serialize a directly
self-referencing object graph by default, which is what
`model/Unserializable.java` demonstrates in both projects (a cycle, not an
empty class, precisely because the empty-class case stopped failing).

### 7. A Spring Boot autoconfiguration trap, not a Jackson one

This one cost real debugging time while building this project, so it's
worth its own section. The "obvious" Jackson 2 pattern - define your own
`@Bean ObjectMapper` and let Spring wire it into the JSON
`HttpMessageConverter` - **silently stops working when you port it to
Jackson 3 on Spring Boot 4.1**. Boot 4.1's Jackson 3 autoconfiguration
(`spring-boot-jackson` module) builds its own internal `JsonMapper` via
`JsonMapper.builder()` plus any `JsonMapperBuilderCustomizer` beans it
finds - it does **not** look for a user-supplied `ObjectMapper` bean the
way the old Jackson 2 autoconfiguration did.

Ship a naive `@Bean ObjectMapper` in a Jackson 3 project and everything
*looks* fine - the bean exists, injects fine into your own components - but
REST responses serialized through Spring MVC's message converter quietly
use a DIFFERENT, uncustomized mapper. This project's own test suite caught
exactly that: `sponsorshipFee` came back as a raw
`{"amount":49.99,"currency":"USD"}` object over HTTP instead of the custom
`"49.99 USD"` string, even though the custom serializer worked perfectly
when called directly. The fix - implement `JsonMapperBuilderCustomizer`
instead of replacing the bean outright - is in both projects'
`config/ObjectMapperConfig.java` (jackson2-example still uses the bean
pattern since that one genuinely works on Boot 3; jackson3-example uses the
customizer).

### 8. What does NOT change

* **Records.** Native since Jackson 2.12 (2020); unchanged in Jackson 3. No
  extra module, no `@JsonCreator`, in either version. See `CommentDto.java`.
* **The tree model** (`JsonNode`/`ObjectNode`). Same method names
  (`put`, `get`, `valueToTree`, ...), just a new package. See
  `TreeModelEnricher.java` - the only diff between the two projects' copies
  of that file is the import lines.
* **Enums, `@JsonProperty`, `@JsonInclude`, `@JsonCreator`.** All identical.

---

## Spring Boot's role in this migration

Spring Boot 3.x only ever shipped Jackson 2. **Spring Boot 4.0 was the first
line to default to Jackson 3** - `spring-boot-starter-web` on Boot 4.1 pulls
in `spring-boot-starter-jackson` (Jackson 3) transitively, confirmed directly
from the `spring-boot-starter-web:4.1.0` POM.

If you can't flip your whole codebase at once, Boot 4.1 also still manages
Jackson 2 (`com.fasterxml.jackson:jackson-bom`) and ships a
`spring-boot-jackson2` autoconfiguration module for exactly this purpose -
letting you keep Jackson 2 wiring in a Boot 4.x app while you migrate
incrementally. This project doesn't demonstrate that hybrid mode on purpose:
`jackson2-example` and `jackson3-example` are kept as clean, single-Jackson-
version projects so every diff you see is a genuine Jackson 2 vs. 3 diff,
not an artifact of running both at once.

**Unrelated-but-you'll-hit-it note:** Spring Boot 4.1 (Spring Framework 7)
also removed `TestRestTemplate` in favor of the new `RestTestClient`
(a WebTestClient-style fluent API). Not a Jackson change, but if you're
moving a test suite across this same Boot major version bump, budget time
for it - see `jackson3-example`'s `BlogPostControllerTest.java`.

---

## Feature tour (a map for the article)

| Feature | jackson2-example file | jackson3-example file |
|---|---|---|
| Package rename, annotations unchanged | `model/Author.java`, `model/BlogPost.java` | same |
| `Optional<T>` handling | `model/Author.java` | same |
| `java.time.Instant` handling | `model/BlogPost.java` | same |
| Records as JSON DTOs (unchanged) | `model/CommentDto.java` | same |
| Immutable vs. mutable `ObjectMapper` | `config/ObjectMapperConfig.java` | same |
| Custom serializer/deserializer, `Module`→`JacksonModule` rename | `json/Money*.java` | same |
| Checked vs. unchecked exceptions | `web/BlogPostController.java`, `web/JacksonErrorAdvice.java` | same |
| Low-level streaming API, `*Field`→`*Property` rename | `json/StreamingWriter.java` | same |
| Tree model (`JsonNode`) | `json/TreeModelEnricher.java` | same |
| A deliberate serialization failure (`FAIL_ON_EMPTY_BEANS`) | `model/Unserializable.java` | same |

## Running it yourself

```bash
cd jackson2-example && ./gradlew bootRun   # :8082
cd jackson3-example && ./gradlew bootRun   # :8083

curl localhost:8082/api/posts/sample | jq   # Jackson 2 response
curl localhost:8083/api/posts/sample | jq   # Jackson 3 response - identical JSON
```

Both projects' test suites exercise every feature in the table above end to
end (`./gradlew test` in each directory) - a green run on both sides is
itself evidence for the guide: same behavior, less ceremony required to get
there in Jackson 3.

## Version matrix

| Component | jackson2-example | jackson3-example |
|---|---|---|
| Java | 21 | 21 |
| Spring Boot | 3.5.16 | 4.1.0 |
| Jackson | 2.21.4 | 3.1.4 (via Spring Boot's BOM) |
| jackson-annotations | 2.21 (com.fasterxml, both versions) | 2.22 (com.fasterxml, both versions) |
