import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

/// Demo 4: generating JSON. Flight tracks to a GeoJSON FeatureCollection.
///
/// There is no builder and no data binding, only factory methods:
/// JsonObject.of(Map), JsonArray.of(List), JsonString.of, JsonNumber.of,
/// JsonBoolean.of and JsonNull.of. This shows what that looks like raw, and
/// how small a helper makes it pleasant.
///
///     java --add-modules jdk.incubator.json src/GenerateGeoJson.java
///
/// Paste out/tracks.geojson into https://geojson.io to see the tracks.

record Position(double latitude, double longitude, int altitudeFt) {}

record Track(String callsign, String icao24, boolean military, String squawk, List<Position> positions) {}

void main() throws Exception {
    var tracks = List.of(
            new Track("AAL1942", "a4b2c1", false, "3341", List.of(
                    new Position(39.9012, -74.9810, 6000),
                    new Position(39.8890, -75.0900, 4200),
                    new Position(39.8790, -75.1800, 2500),
                    new Position(39.8721, -75.2412, 1100))),
            new Track("N77MED", "a6d5e4", false, "7700", List.of(
                    new Position(39.9100, -75.0600, 2200),
                    new Position(39.9350, -75.1200, 1800),
                    new Position(39.9526, -75.1650, 1500))),
            new Track("RCH415", "ae07f2", true, null, List.of(
                    new Position(40.0151, -74.5911, 23000),
                    new Position(39.9900, -74.8000, 25000),
                    new Position(39.9600, -75.0500, 27000))));

    // 1. Raw factory methods. Correct, and you can see why nobody writes it twice.
    JsonValue raw = JsonObject.of(Map.of(
            "type", JsonString.of("Point"),
            "coordinates", JsonArray.of(List.of(JsonNumber.of(-75.2412), JsonNumber.of(39.8721)))));
    IO.println("Raw factories:  " + raw);

    // 2. With a three-method helper (below), building reads like the output.
    JsonValue featureCollection = obj(
            "type", "FeatureCollection",
            "features", tracks.stream().map(this::toFeature).toList());

    String compact = featureCollection.toString();
    IO.println("Compact:        %d chars".formatted(compact.length()));

    String pretty = Json.toDisplayString(featureCollection, "  ");
    Path out = Path.of("out/tracks.geojson");
    Files.createDirectories(out.getParent());
    Files.writeString(out, pretty);
    IO.println("Pretty:         %d chars, written to %s%n".formatted(pretty.length(), out));
    IO.println(Json.toDisplayString(toFeature(tracks.get(1)), "  "));

    // 3. Round trip. JsonValue has no equals() semantics, so compare text.
    JsonValue reparsed = Json.parse(pretty);
    IO.println("\nRound trip equal (by toString): " + reparsed.toString().equals(compact));
    IO.println("Round trip equal (by equals):   " + reparsed.equals(featureCollection) + "  <- unspecified, don't rely on it");
}

JsonValue toFeature(Track t) {
    return obj(
            "type", "Feature",
            "geometry", obj(
                    "type", "LineString",
                    // GeoJSON is [longitude, latitude, altitude], the reverse of how pilots say it.
                    "coordinates", t.positions().stream()
                            .map(p -> List.of(p.longitude(), p.latitude(), Math.round(p.altitudeFt() * 0.3048)))
                            .toList()),
            "properties", obj(
                    "callsign", t.callsign(),
                    "icao24", t.icao24(),
                    "military", t.military(),
                    "squawk", t.squawk(),               // null -> JSON null
                    "points", t.positions().size()));
}

// ---------------------------------------------------------------------------
// The helper. Insertion order is kept: JsonObject.of preserves the iteration
// order of the map you hand it, so a LinkedHashMap gives predictable output.
// ---------------------------------------------------------------------------

JsonObject obj(Object... nameValuePairs) {
    var members = new LinkedHashMap<String, JsonValue>();
    for (int i = 0; i < nameValuePairs.length; i += 2) {
        members.put((String) nameValuePairs[i], json(nameValuePairs[i + 1]));
    }
    return JsonObject.of(members);
}

JsonValue json(Object o) {
    return switch (o) {
        case null -> JsonNull.of();
        case JsonValue v -> v;
        case String s -> JsonString.of(s);
        case Integer i -> JsonNumber.of(i);
        case Long l -> JsonNumber.of(l);
        case Double d -> JsonNumber.of(d);
        case Boolean b -> JsonBoolean.of(b);
        case List<?> list -> JsonArray.of(list.stream().map(this::json).toList());
        default -> throw new IllegalArgumentException("No JSON mapping for " + o.getClass());
    };
}
