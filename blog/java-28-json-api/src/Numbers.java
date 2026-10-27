import jdk.incubator.json.Json;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonValue;
import jdk.incubator.json.JsonValueException;

import java.util.function.Function;

/// Demo 6: JSON numbers are arbitrary precision. Java primitives are not.
///
/// asInt()/asLong() succeed only when the value converts exactly. asDouble()
/// rounds to the nearest double but refuses anything that would become
/// Infinity. For everything else, the JsonNumber's text goes into BigDecimal.
///
///     java --add-modules jdk.incubator.json src/Numbers.java
void main() {
    String[] inputs = {
            "123", "123.0", "234.56E2", "345.6", "2147483647", "2147483648",
            "9007199254740993", "9223372036854775808", "-0",
            "3.141592653589793238462643383279", "1.8E309", "1E-400"
    };

    IO.println("%-34s %-19s %-22s %-22s %s".formatted("JSON text", "asInt()", "asLong()", "asDouble()", "BigDecimal"));
    for (String text : inputs) {
        JsonValue n = Json.parse(text);
        IO.println("%-34s %-19s %-22s %-22s %s".formatted(
                text,
                attempt(n, JsonValue::asInt),
                attempt(n, JsonValue::asLong),
                attempt(n, JsonValue::asDouble),
                new BigDecimal(n.toString()).toPlainString().transform(s -> s.length() > 30 ? s.substring(0, 27) + "..." : s)));
    }

    // toString() keeps the source text, so trailing zeros and exponents survive a round trip.
    IO.println("\nSource text survives: " + Json.parse("[1.10, 1E2, 0.000]"));

    // Money-style values: never go through double.
    BigDecimal total = Json.parse("[19.99, 0.01, 4.10]").asList().stream()
            .map(v -> new BigDecimal(v.toString()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    double viaDouble = Json.parse("[0.1, 0.2]").asList().stream().mapToDouble(JsonValue::asDouble).sum();
    IO.println("BigDecimal sum of [19.99, 0.01, 4.10] = " + total);
    IO.println("double sum of [0.1, 0.2]              = " + viaDouble);

    // Factories for generating numbers.
    IO.println("\nJsonNumber.of(42)              -> " + JsonNumber.of(42));
    IO.println("JsonNumber.of(42L)             -> " + JsonNumber.of(42L));
    IO.println("JsonNumber.of(0.1)             -> " + JsonNumber.of(0.1));
    IO.println("JsonNumber.of(\"1.50\")          -> " + JsonNumber.of("1.50"));
    IO.println("JsonNumber.of(Double.NaN)      -> " + attempt(Double.NaN, JsonNumber::of));
    IO.println("JsonNumber.of(\"0x1F\")          -> " + attempt("0x1F", JsonNumber::of));
}

/// Runs a conversion and returns its result, or the exception's simple name.
<T> String attempt(T input, Function<T, ?> conversion) {
    try {
        return String.valueOf(conversion.apply(input));
    } catch (JsonValueException | IllegalArgumentException e) {
        return e.getClass().getSimpleName();
    }
}
