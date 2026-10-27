import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// The same five jobs done with jdk.incubator.json and with Jackson 3, so the
/// snippets in the article are code that ran, not code from memory.
///
///     ./fetch-jackson.sh
///     java --add-modules jdk.incubator.json -cp 'lib/*' SideBySide.java

record Period(String name, int temperature, Optional<String> windGust) {}

void main() throws Exception {
    String body = Files.readString(Path.of("../data/forecast.json"));
    JsonMapper mapper = JsonMapper.builder().build();

    // 1. Average of a nested array --------------------------------------------
    double jdkAvg = Json.parse(body)
            .get("properties").get("periods").asList().stream()
            .mapToInt(p -> p.get("temperature").asInt())
            .average().orElseThrow();

    double jacksonAvg = mapper.readTree(body)
            .get("properties").get("periods").valueStream()
            .mapToInt(p -> p.get("temperature").asInt())
            .average().orElseThrow();

    IO.println("1. average           jdk=%.2f  jackson=%.2f".formatted(jdkAvg, jacksonAvg));

    // 2. Optional member, nullable value --------------------------------------
    JsonValue jdkPeriod = Json.parse(body).get("properties").get("periods").get(2);
    String jdkGust = jdkPeriod.tryGet("windGust").map(JsonValue::asString).orElse("-");
    String jdkPrecip = jdkPeriod.get("probabilityOfPrecipitation").get("value").tryValue()
            .map(v -> v.asInt() + "%").orElse("-");

    JsonNode jacksonPeriod = mapper.readTree(body).get("properties").get("periods").get(2);
    String jacksonGust = jacksonPeriod.path("windGust").asString("-");
    JsonNode precipNode = jacksonPeriod.path("probabilityOfPrecipitation").path("value");
    String jacksonPrecip = precipNode.isNumber() ? precipNode.asInt() + "%" : "-";

    IO.println("2. gust/precip       jdk=%s/%s  jackson=%s/%s".formatted(jdkGust, jdkPrecip, jacksonGust, jacksonPrecip));

    // 3. Build a small document -----------------------------------------------
    // Map.of has no defined order; use a LinkedHashMap when member order matters.
    var members = new LinkedHashMap<String, JsonValue>();
    members.put("type", JsonString.of("Point"));
    members.put("coordinates", JsonArray.of(List.of(JsonNumber.of(-75.2412), JsonNumber.of(39.8721))));
    JsonValue jdkDoc = JsonObject.of(members);

    ObjectNode jacksonDoc = mapper.createObjectNode();
    jacksonDoc.put("type", "Point");
    jacksonDoc.putArray("coordinates").add(-75.2412).add(39.8721);

    IO.println("3. generate          jdk=%s%n                     jackson=%s".formatted(jdkDoc, mapper.writeValueAsString(jacksonDoc)));

    // 4. Into records ---------------------------------------------------------
    // JDK: no binding. You write the mapping (or a reflective helper, see RecordBinding.java).
    List<Period> jdkPeriods = Json.parse(body).get("properties").get("periods").asList().stream()
            .map(p -> new Period(
                    p.get("name").asString(),
                    p.get("temperature").asInt(),
                    p.tryGet("windGust").map(JsonValue::asString)))
            .toList();

    // Jackson: one call. Unknown members are ignored by default in 3.x, and
    // Optional support is built into databind.
    List<Period> jacksonPeriods = mapper.readValue(
            mapper.readTree(body).get("properties").get("periods").toString(),
            mapper.getTypeFactory().constructCollectionType(List.class, Period.class));

    IO.println("4. records equal     " + jdkPeriods.equals(jacksonPeriods) + "  (" + jdkPeriods.get(2) + ")");

    // 5. Duplicate member names -----------------------------------------------
    String dup = """
            {"squawk": "1200", "squawk": "7700"}""";
    String jdkDup;
    try {
        jdkDup = Json.parse(dup).toString();
    } catch (JsonParseException e) {
        jdkDup = "rejected";
    }
    String jacksonDup = mapper.readTree(dup).toString();
    String jacksonStrict;
    try {
        JsonMapper strict = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        jacksonStrict = strict.readTree(dup).toString();
    } catch (JacksonException e) {
        jacksonStrict = "rejected";
    }
    IO.println("5. duplicate names   jdk=%s  jackson(default)=%s  jackson(strict)=%s".formatted(jdkDup, jacksonDup, jacksonStrict));
}
