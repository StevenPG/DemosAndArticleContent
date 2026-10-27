import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;

/// Demo 2: positional, null-heavy JSON. OpenSky Network ADS-B state vectors.
///
/// Each aircraft is an array, not an object, and a third of the slots can be
/// null when a transponder has not reported them yet. This is where tryValue()
/// earns its keep.
///
///     java --add-modules jdk.incubator.json src/AircraftStates.java
///
/// Field order from https://openskynetwork.github.io/opensky-api/rest.html
/// (index 17, category, is only sent when the request has extended=1).

static final double FEET_PER_METRE = 1 / 0.3048;

static final List<String> CATEGORIES = List.of(
        "No info", "No ADS-B category", "Light", "Small", "Large", "High vortex large",
        "Heavy", "High performance", "Rotorcraft", "Glider", "Lighter-than-air",
        "Parachutist", "Ultralight", "Reserved", "UAV", "Space vehicle",
        "Emergency vehicle", "Service vehicle", "Point obstacle", "Cluster obstacle", "Line obstacle");

record Position(double longitude, double latitude, Double baroAltitudeMetres) {}

record StateVector(
        String icao24,
        Optional<String> callsign,
        String originCountry,
        Optional<Position> position,
        boolean onGround,
        Double velocityMs,
        Optional<String> squawk,
        String category) {

    static StateVector from(JsonValue s) {
        // Positional access: get(int) on the array, then convert.
        Optional<Position> position = s.get(5).tryValue().isPresent() && s.get(6).tryValue().isPresent()
                ? Optional.of(new Position(s.get(5).asDouble(), s.get(6).asDouble(), nullableDouble(s.get(7))))
                : Optional.empty();

        return new StateVector(
                s.get(0).asString(),
                // null on the wire, or blank padding: both mean "no callsign".
                s.get(1).tryValue().map(JsonValue::asString).map(String::strip).filter(cs -> !cs.isEmpty()),
                s.get(2).asString(),
                position,
                s.get(8).asBoolean(),
                nullableDouble(s.get(9)),
                s.get(14).tryValue().map(JsonValue::asString),
                // Arrays can be shorter than you expect; asList().size() guards get(int).
                s.asList().size() > 17 ? CATEGORIES.get(s.get(17).asInt()) : "n/a");
    }

    String flightLevel() {
        return position.map(Position::baroAltitudeMetres)
                .map(m -> "FL%03d".formatted(Math.round(m * FEET_PER_METRE / 100)))
                .orElse(onGround ? "GND" : "?");
    }

    boolean emergency() {
        return squawk.filter(sq -> Set.of("7500", "7600", "7700").contains(sq)).isPresent();
    }
}

static Double nullableDouble(JsonValue v) {
    return v.tryValue().map(JsonValue::asDouble).orElse(null);
}

void main() throws Exception {
    JsonValue root = Json.parse(Files.readString(Path.of("data/states.json")));

    // OpenSky sends "states": null when the bounding box is empty.
    List<StateVector> states = root.get("states").tryValue()
            .map(JsonValue::asList)
            .orElse(List.of())
            .stream()
            .map(StateVector::from)
            .toList();

    IO.println("Snapshot at " + Instant.ofEpochSecond(root.get("time").asLong()) + ", " + states.size() + " aircraft\n");
    IO.println("%-7s %-9s %-14s %-6s %7s  %-5s %s".formatted("ICAO24", "Callsign", "Country", "Level", "GS kt", "Sqwk", "Category"));

    states.stream()
            .sorted(Comparator.comparing(
                    (StateVector sv) -> sv.position().map(Position::baroAltitudeMetres).orElse(null),
                    Comparator.nullsLast(Comparator.<Double>reverseOrder())))
            .forEach(sv -> IO.println("%-7s %-9s %-14s %-6s %7s  %-5s %s%s".formatted(
                    sv.icao24(),
                    sv.callsign().orElse("-"),
                    sv.originCountry(),
                    sv.flightLevel(),
                    sv.velocityMs() == null ? "-" : "%.0f".formatted(sv.velocityMs() * 1.943844),
                    sv.squawk().orElse("-"),
                    sv.category(),
                    sv.emergency() ? "   << EMERGENCY" : "")));

    long noPosition = states.stream().filter(sv -> sv.position().isEmpty()).count();
    long cruising = states.stream()
            .flatMap(sv -> sv.position().stream())
            .map(Position::baroAltitudeMetres)
            .filter(Objects::nonNull)
            .filter(m -> m * FEET_PER_METRE >= 30_000)
            .count();
    IO.println("%nAt or above FL300: %d   No position yet: %d".formatted(cruising, noPosition));
}
