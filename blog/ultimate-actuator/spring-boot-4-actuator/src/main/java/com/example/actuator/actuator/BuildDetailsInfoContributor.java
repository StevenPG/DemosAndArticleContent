package com.example.actuator.actuator;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * A custom {@link InfoContributor}. Anything contributed here is merged into the
 * {@code /actuator/info} response alongside the built-in {@code env}, {@code java},
 * {@code os}, {@code build} (from {@code build-info.properties}) and {@code git}
 * contributors.
 */
@Component
public class BuildDetailsInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("demo", Map.of(
                "name", "Ultimate Spring Boot 3 Actuator demo",
                "team", "Platform Engineering",
                "startedAt", Instant.now().toString(),
                "docs", "https://docs.spring.io/spring-boot/reference/actuator/index.html"));
    }
}
