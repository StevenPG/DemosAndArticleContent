import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import jdk.incubator.json.JsonValueException;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import java.lang.management.ManagementFactory;

/// Demo 3: pattern matching over the sealed JsonValue hierarchy.
///
/// The JVM dumps its own threads as JSON (the same format as
/// `jcmd <pid> Thread.dump_to_file -format=json`), then a 15-line exhaustive
/// switch walks the tree and prints a "schema sketch": every path seen and
/// every JSON type found there. Point it at any document you have never seen.
///
///     java --add-modules jdk.incubator.json src/ThreadDumpSchema.java [file.json]
void main(String[] args) throws Exception {
    String text = args.length > 0 ? Files.readString(Path.of(args[0])) : dumpOwnThreads();
    JsonValue root = Json.parse(text);

    var schema = new TreeMap<String, Set<String>>();
    walk("", root, schema);

    IO.println("Schema sketch (%d paths):%n".formatted(schema.size()));
    schema.forEach((path, types) -> IO.println("  %-60s %s".formatted(path, String.join(" | ", types))));

    if (args.length == 0) {
        IO.println("\nThreads in this JVM:");
        root.get("threadDump").get("threadContainers").asList().forEach(container ->
                container.get("threads").asList().forEach(thread -> IO.println("  tid=%-4d %-28s %s".formatted(
                        tid(thread),
                        thread.get("name").asString(),
                        thread.get("state").asString()))));
    }
}

/// The whole walker. JsonValue is sealed over exactly six subtypes, so this
/// switch is exhaustive with no default branch. If a future revision of the
/// API added a seventh type, this would stop compiling instead of silently
/// falling through.
void walk(String path, JsonValue value, Map<String, Set<String>> schema) {
    String type = switch (value) {
        case JsonObject o -> {
            o.asMap().forEach((name, member) -> walk(path + "/" + name, member, schema));
            yield "object";
        }
        case JsonArray a -> {
            a.asList().forEach(element -> walk(path + "[]", element, schema));
            yield "array(" + a.asList().size() + ")";
        }
        case JsonString _ -> "string";
        case JsonNumber _ -> "number";
        case JsonBoolean _ -> "boolean";
        case JsonNull _ -> "null";
    };
    // Array sizes vary per element; collapse them so the sketch stays readable.
    schema.computeIfAbsent(path.isEmpty() ? "/" : path, _ -> new TreeSet<>())
            .add(type.startsWith("array") ? "array" : type);
}

/// Thread ids were JSON strings in JDK 26 and earlier and JSON numbers from
/// JDK 27 on. Type patterns handle both without a try/catch.
long tid(JsonValue thread) {
    return switch (thread.get("tid")) {
        case JsonNumber n -> n.asLong();
        case JsonString s -> Long.parseLong(s.asString());
        default -> throw new JsonValueException("Unexpected type for \"tid\": " + thread.get("tid"));
    };
}

String dumpOwnThreads() throws Exception {
    // A few extra threads so the dump has more than one container to look at.
    var pool = Executors.newFixedThreadPool(2);
    var latch = new CountDownLatch(1);
    pool.submit(() -> { latch.await(); return null; });
    pool.submit(() -> { latch.await(); return null; });

    try {
        Path file = Files.createTempDirectory("json-demo").resolve("threads.json");
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                .dumpThreads(file.toAbsolutePath().toString(), ThreadDumpFormat.JSON);
        return Files.readString(file);
    } finally {
        latch.countDown();
        pool.shutdown();
    }
}
