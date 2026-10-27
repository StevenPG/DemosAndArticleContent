import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import jdk.incubator.json.JsonValueException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;

/// Demo 7: the API deliberately has no data binding. Records plus about 60
/// lines of reflection get you most of the way for simple, well-behaved
/// documents. Read this as "here is where Jackson starts earning its jar",
/// not as a library to ship.
///
///     java --add-modules jdk.incubator.json src/RecordBinding.java

enum Compass { N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW }

record Forecast(Properties properties) {}

record Properties(String units, Elevation elevation, List<Period> periods) {}

record Elevation(String unitCode, double value) {}

record Precipitation(String unitCode, Optional<Integer> value) {}

record Period(
        int number,
        String name,
        boolean isDaytime,
        int temperature,
        String temperatureUnit,
        Optional<String> temperatureTrend,      // present-but-null in the feed
        Precipitation probabilityOfPrecipitation,
        String windSpeed,
        Optional<String> windGust,              // absent on most periods
        Compass windDirection,
        String shortForecast) {}

void main() throws Exception {
    JsonValue root = Json.parse(Files.readString(Path.of("data/forecast.json")));

    // JSON -> records. Unknown members ("@context", "geometry", ...) are ignored.
    Forecast forecast = bind(root, Forecast.class);
    IO.println("Bound %d periods, elevation %.1f %s%n".formatted(
            forecast.properties().periods().size(),
            forecast.properties().elevation().value(),
            forecast.properties().elevation().unitCode()));
    forecast.properties().periods().forEach(IO::println);

    // Records -> JSON, after doing ordinary Java things with ordinary Java types.
    List<Period> gusty = forecast.properties().periods().stream()
            .filter(p -> p.windGust().isPresent())
            .toList();
    IO.println("\nGusty periods as JSON:\n" + Json.toDisplayString(toJson(gusty), "  "));

    // A wrong type in the record fails with the JSON path in the message.
    record Wrong(String name, String temperature) {}
    try {
        bind(root.get("properties").get("periods").get(0), Wrong.class);
    } catch (JsonValueException e) {
        IO.println("\nBinding into the wrong type:\n  " + e.getMessage());
    }
}

// ---------------------------------------------------------------------------
// JSON -> Java
// ---------------------------------------------------------------------------

@SuppressWarnings("unchecked")
<T> T bind(JsonValue json, Type type) {
    Object result = switch (type) {
        case Class<?> c when c == String.class -> json.asString();
        case Class<?> c when c == int.class || c == Integer.class -> json.asInt();
        case Class<?> c when c == long.class || c == Long.class -> json.asLong();
        case Class<?> c when c == double.class || c == Double.class -> json.asDouble();
        case Class<?> c when c == boolean.class || c == Boolean.class -> json.asBoolean();
        case Class<?> c when c == BigDecimal.class -> new BigDecimal(json.toString());
        case Class<?> c when c.isEnum() -> enumConstant(c, json.asString());
        case Class<?> c when c.isRecord() -> bindRecord(json, c);
        case ParameterizedType p when p.getRawType() == List.class ->
                json.asList().stream().map(e -> bind(e, p.getActualTypeArguments()[0])).toList();
        case ParameterizedType p when p.getRawType() == Optional.class ->
                json.tryValue().map(e -> bind(e, p.getActualTypeArguments()[0]));
        default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
    return (T) result;
}

Object bindRecord(JsonValue json, Class<?> recordType) {
    RecordComponent[] components = recordType.getRecordComponents();
    Object[] args = new Object[components.length];
    Class<?>[] types = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
        RecordComponent rc = components[i];
        types[i] = rc.getType();
        args[i] = rc.getType() == Optional.class
                // Missing member and null member both become Optional.empty().
                ? json.tryGet(rc.getName()).flatMap(JsonValue::tryValue)
                        .map(v -> bind(v, ((ParameterizedType) rc.getGenericType()).getActualTypeArguments()[0]))
                // Required: get() throws with the path if the member is missing.
                : bind(json.get(rc.getName()), rc.getGenericType());
    }
    try {
        var constructor = recordType.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
    }
}

@SuppressWarnings({"unchecked", "rawtypes"})
Object enumConstant(Class<?> enumType, String name) {
    return Enum.valueOf((Class) enumType, name);
}

// ---------------------------------------------------------------------------
// Java -> JSON
// ---------------------------------------------------------------------------

JsonValue toJson(Object o) {
    return switch (o) {
        case null -> JsonNull.of();
        case String s -> JsonString.of(s);
        case Integer i -> JsonNumber.of(i);
        case Long l -> JsonNumber.of(l);
        case Double d -> JsonNumber.of(d);
        case BigDecimal bd -> JsonNumber.of(bd.toString());
        case Boolean b -> JsonBoolean.of(b);
        case Enum<?> e -> JsonString.of(e.name());
        case Optional<?> opt -> opt.map(this::toJson).orElse(JsonNull.of());
        case List<?> list -> JsonArray.of(list.stream().map(this::toJson).toList());
        case Record r -> {
            var members = new LinkedHashMap<String, JsonValue>();
            for (RecordComponent rc : r.getClass().getRecordComponents()) {
                try {
                    var accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    members.put(rc.getName(), toJson(accessor.invoke(r)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            yield JsonObject.of(members);
        }
        default -> throw new IllegalArgumentException("No JSON mapping for " + o.getClass());
    };
}
