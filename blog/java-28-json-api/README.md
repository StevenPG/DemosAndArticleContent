# Java 28's Built-in JSON API (JEP 540): Seven Runnable Demos

JDK 28 ships **[JEP 540: Simple JSON API (Incubator)](https://openjdk.org/jeps/540)**,
the `jdk.incubator.json` module. It parses and generates RFC 8259 JSON with no
dependency at all. It does not do data binding or streaming, and it has no
lenient mode.

This project is a tour of the whole API surface through seven single-file
programs. None of them need a build tool or a dependency.

Accompanies the post **[Java 28 Has a Built-in JSON API: An Introduction to JEP 540](https://stevenpg.com/posts/java-28-built-in-json-api-jep-540/)**.

## Requirements

- **JDK 28** (early access build 21 or later, from <https://jdk.java.net/28/>).
- Nothing else. Every demo runs with the source launcher.

Incubator modules are not resolved by default, so every compile and run needs
`--add-modules jdk.incubator.json`. The JVM prints
`WARNING: Using incubator modules: jdk.incubator.json` on startup. That is expected.

## Quick start

```bash
./run.sh                 # every demo, in order
./run.sh Strictness      # one demo
java --add-modules jdk.incubator.json src/Forecast.java --live   # hit api.weather.gov
```

## The demos

| File | What it shows | API |
|---|---|---|
| `src/Forecast.java` | The JEP's weather example, extended: average temperature, a table of periods, optional members, nullable values | `Json.parse`, `get`, `asList`, `asInt`, `asString`, `tryGet`, `tryValue`, `asMap` |
| `src/AircraftStates.java` | OpenSky-format ADS-B state vectors: positional arrays full of nulls, mapped to records | `get(int)`, `tryValue`, `asDouble`, `asBoolean`, `asLong` |
| `src/ThreadDumpSchema.java` | The JVM dumps its own threads as JSON; an exhaustive `switch` over the sealed `JsonValue` prints a schema sketch of any document | Pattern matching on `JsonObject`/`JsonArray`/`JsonString`/`JsonNumber`/`JsonBoolean`/`JsonNull` |
| `src/GenerateGeoJson.java` | Flight tracks to GeoJSON: raw factory methods, a 20-line helper, pretty printing, round trip | `JsonObject.of`, `JsonArray.of`, `JsonString.of`, `JsonNumber.of`, `Json.toDisplayString` |
| `src/Strictness.java` | 17 inputs (duplicates, trailing commas, comments, NaN, ...) and the exact error for each | `JsonParseException`, `getErrorLine`, `getErrorPosition` |
| `src/Numbers.java` | Exact `asInt`/`asLong`, rounding `asDouble`, lossless `BigDecimal` | `JsonNumber`, `JsonValueException` |
| `src/RecordBinding.java` | No data binding is built in, so here is about 60 lines of reflection that binds JSON to records and back | Everything above, plus `java.lang.reflect.RecordComponent` |

Captured output from every demo is in `results/output.txt` (JDK `28-ea+21-1707`, Linux x64).

## Jackson side by side

`jackson-comparison/SideBySide.java` does five jobs both ways: averaging a
nested array, reading an optional member and a nullable value, building a
small document, binding to records, and handling duplicate names. Both
libraries give identical results for the first four. For the fifth, Jackson's
default keeps the last duplicate and the JDK always rejects the document.

```bash
cd jackson-comparison
./fetch-jackson.sh      # jackson-core/databind 3.2.4 + annotations 2.22 into lib/
java --add-modules jdk.incubator.json -cp 'lib/*' SideBySide.java
```

Output: `results/side-by-side.txt`.

## Data

- `data/forecast.json`: shaped like the real `api.weather.gov` response for
  gridpoint `MTR/97,83` (Santa Clara, CA, the one JEP 540 uses), trimmed to six
  periods. Run `Forecast.java --live` to use the real thing.
- `data/states.json`: **synthetic** aircraft in the
  [OpenSky Network `/states/all`](https://openskynetwork.github.io/opensky-api/rest.html)
  format, placed around Philadelphia. The ICAO24 addresses and flight numbers
  are invented. The null patterns match what the live feed produces: no
  position yet, no callsign, no squawk, aircraft on the ground.

## Using it in a Gradle or Maven build

The flag must reach `javac`, the test JVM, and the runtime JVM.

```kotlin
// build.gradle.kts
java { toolchain { languageVersion = JavaLanguageVersion.of(28) } }
tasks.withType<JavaCompile> { options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.json")) }
tasks.withType<Test> { jvmArgs("--add-modules", "jdk.incubator.json") }
tasks.withType<JavaExec> { jvmArgs("--add-modules", "jdk.incubator.json") }
```

```xml
<!-- pom.xml, maven-compiler-plugin -->
<configuration>
  <release>28</release>
  <compilerArgs>
    <arg>--add-modules</arg>
    <arg>jdk.incubator.json</arg>
  </compilerArgs>
</configuration>
<!-- and the same two args in surefire's <argLine> and your launcher -->
```

For a modular application, use `requires jdk.incubator.json;` in
`module-info.java` instead of the flag.

## Caveat

This is an **incubator** API. Package name, method names and behaviour can
change between JDK 28 and its eventual final form. Everything here was run on
JDK 28 early-access build 21 in October 2026.
