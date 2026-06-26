package com.example.actuator.web;

import com.example.actuator.config.DemoProperties;
import io.micrometer.observation.annotation.Observed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny endpoint that reads a value from {@link DemoProperties}. Useful for
 * generating trace/metric traffic and for seeing the {@code demo.*} properties in
 * the {@code configprops} and {@code env} endpoints.
 */
@RestController
public class GreetingController {

    private final DemoProperties properties;

    public GreetingController(DemoProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/greeting")
    @Observed(name = "greeting.read", contextualName = "greeting")
    public Map<String, Object> greeting() {
        return Map.of(
                "message", properties.greeting(),
                "featureX", properties.featureX());
    }
}
