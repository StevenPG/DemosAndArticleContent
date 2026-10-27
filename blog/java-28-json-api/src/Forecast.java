import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;

// Compact source files auto-import java.base only; the HTTP client lives in java.net.http.
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/// Demo 1: parse a National Weather Service forecast and pull data out of it.
///
/// Reads data/forecast.json by default. Pass `--live` to fetch the same
/// gridpoint (Santa Clara, CA, the one JEP 540 uses) from api.weather.gov.
///
///     java --add-modules jdk.incubator.json src/Forecast.java [--live]
void main(String[] args) throws Exception {
    String body = List.of(args).contains("--live")
            ? fetchLive("https://api.weather.gov/gridpoints/MTR/97,83/forecast")
            : Files.readString(Path.of("data/forecast.json"));

    JsonValue root = Json.parse(body);

    // The JEP's headline example: no casts, no DTOs, one chain.
    root.get("properties").get("periods").asList().stream()
            .mapToInt(p -> p.get("temperature").asInt())
            .average()
            .ifPresent(avg -> IO.println("Average forecast temperature: %.1f°F%n".formatted(avg)));

    // Navigate once, then reuse the List<JsonValue>.
    List<JsonValue> periods = root.get("properties").get("periods").asList();

    IO.println("%-16s %5s  %-6s %-8s %s".formatted("Period", "Temp", "Precip", "Gusts", "Forecast"));
    for (JsonValue p : periods) {
        // "windGust" is a member that only appears on some periods: tryGet.
        String gusts = p.tryGet("windGust").map(JsonValue::asString).orElse("-");

        // "probabilityOfPrecipitation.value" is always present but may be null: tryValue.
        String precip = p.get("probabilityOfPrecipitation").get("value").tryValue()
                .map(v -> v.asInt() + "%")
                .orElse("-");

        IO.println("%-16s %3d°%s  %-6s %-8s %s".formatted(
                p.get("name").asString(),
                p.get("temperature").asInt(),
                p.get("temperatureUnit").asString(),
                precip,
                gusts,
                p.get("shortForecast").asString()));
    }

    // Deep navigation reads like a path expression.
    double elevation = root.get("properties").get("elevation").get("value").asDouble();
    IO.println("%nGridpoint elevation: %.1f m".formatted(elevation));
    IO.println("Members on properties: " + root.get("properties").asMap().keySet());
}

String fetchLive(String url) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
        var request = HttpRequest.newBuilder(URI.create(url))
                // api.weather.gov rejects requests without a User-Agent.
                .header("User-Agent", "stevenpg.com java-28-json-api demo")
                .header("Accept", "application/geo+json")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
