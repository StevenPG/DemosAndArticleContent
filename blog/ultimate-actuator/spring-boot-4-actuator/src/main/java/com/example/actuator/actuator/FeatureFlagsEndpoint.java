package com.example.actuator.actuator;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fully custom Actuator endpoint exposed at {@code /actuator/featureflags}.
 *
 * <p>Demonstrates all three operation types:
 * <ul>
 *     <li>{@link ReadOperation} &rarr; {@code GET /actuator/featureflags} and
 *         {@code GET /actuator/featureflags/{name}} (the {@link Selector} maps the path segment).</li>
 *     <li>{@link WriteOperation} &rarr; {@code POST /actuator/featureflags/{name}} with a JSON body
 *         {@code {"enabled": true}}.</li>
 *     <li>{@link DeleteOperation} &rarr; {@code DELETE /actuator/featureflags/{name}}.</li>
 * </ul>
 *
 * <p>Because it is annotated with {@link Endpoint} (not {@code @WebEndpoint}), it is
 * automatically available over <em>both</em> HTTP and JMX.
 */
@Component
@Endpoint(id = "featureflags")
public class FeatureFlagsEndpoint {

    private final Map<String, Boolean> flags = new ConcurrentHashMap<>(Map.of(
            "new-checkout", false,
            "beta-search", true));

    @ReadOperation
    public Map<String, Boolean> allFlags() {
        return Collections.unmodifiableMap(flags);
    }

    @ReadOperation
    public Map<String, Object> flag(@Selector String name) {
        return Map.of("name", name, "enabled", flags.getOrDefault(name, false));
    }

    @WriteOperation
    public Map<String, Object> setFlag(@Selector String name, boolean enabled) {
        flags.put(name, enabled);
        return Map.of("name", name, "enabled", enabled);
    }

    @DeleteOperation
    public void removeFlag(@Selector String name) {
        flags.remove(name);
    }
}
