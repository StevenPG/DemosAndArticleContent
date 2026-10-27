import jdk.incubator.json.Json;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonValue;

/// Demo 5: the parser is strict RFC 8259 with no switches. Here is exactly what
/// it accepts and rejects, and what the error message looks like for each.
///
///     java --add-modules jdk.incubator.json src/Strictness.java
void main() {
    var cases = new LinkedHashMap<String, String>();
    cases.put("valid object", """
            {"callsign": "AAL1942", "alt": 1100}""");
    cases.put("top-level scalar", "42");
    cases.put("top-level string", "\"just a string\"");
    cases.put("whitespace around", "  \n\t[1, 2]\n  ");
    cases.put("duplicate member", """
            {
              "squawk": "1200",
              "squawk": "7700"
            }""");
    cases.put("trailing comma", "[1, 2, 3,]");
    cases.put("line comment", """
            {
              // where the plane is
              "alt": 1100
            }""");
    cases.put("single quotes", "{'alt': 1100}");
    cases.put("unquoted name", "{alt: 1100}");
    cases.put("leading zero", "{\"squawk\": 0123}");
    cases.put("NaN", "{\"alt\": NaN}");
    cases.put("Infinity", "[Infinity]");
    cases.put("leading plus", "[+1]");
    cases.put("hex number", "[0x1F]");
    cases.put("raw tab in string", "[\"a\tb\"]");
    cases.put("two documents", "{} {}");
    cases.put("empty input", "");

    IO.println("%-20s %-8s %s".formatted("Input", "Result", "Detail"));
    cases.forEach((label, text) -> {
        try {
            JsonValue v = Json.parse(text);
            IO.println("%-20s %-8s %s".formatted(label, "OK", v));
        } catch (JsonParseException e) {
            // Line and position are zero-based and also available as fields.
            IO.println("%-20s %-8s [line %d, pos %d] %s".formatted(
                    label, "REJECT", e.getErrorLine(), e.getErrorPosition(), e.getMessage()));
        }
    });

    // The JEP's suggested workaround for commented config files: strip, then parse.
    String jsonc = """
            {
              # primary feed
              "feed": "adsb",
              # poll interval, seconds
              "interval": 5
            }""";
    JsonValue config = Json.parse(jsonc.replaceAll("(?m)^\\s*#.*$", ""));
    IO.println("%nStripped '#' comments then parsed: " + config);
}
