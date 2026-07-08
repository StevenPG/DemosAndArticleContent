package com.stevenpg.jackson3example.config;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.stevenpg.jackson3example.json.MoneyModule;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the {@code JsonMapper} Spring Boot builds for you, instead of
 * replacing it with a hand-built {@code ObjectMapper} bean.
 *
 * <p><b>Migration note - a Spring Boot autoconfiguration gotcha, not a
 * Jackson one:</b> jackson2-example's {@code ObjectMapperConfig} defines a
 * plain {@code @Bean ObjectMapper}, and Spring Boot 3's Jackson 2
 * autoconfiguration wires that exact bean into the JSON
 * {@code HttpMessageConverter} - the pattern most Jackson 2 tutorials teach.
 *
 * <p>Porting that same pattern naively to Jackson 3 is a trap: Boot 4.1's
 * Jackson 3 autoconfiguration ({@code JacksonAutoConfiguration} in module
 * {@code spring-boot-jackson}) builds its OWN internal {@code JsonMapper}
 * via {@code JsonMapper.builder()} and a list of
 * {@code JsonMapperBuilderCustomizer} beans - it does NOT look for a
 * user-supplied {@code ObjectMapper} bean the way the Jackson 2 autoconfig
 * did. Define a plain {@code @Bean ObjectMapper} here instead of this
 * customizer, and {@code MoneyModule} would still be injectable elsewhere
 * in the app, but REST responses serialized through Spring MVC's message
 * converter would silently use a mapper that never saw it - exactly the
 * bug this project's own tests caught: {@code sponsorshipFee} came back as
 * a raw {@code {"amount":49.99,"currency":"USD"}} object instead of the
 * custom {@code "49.99 USD"} string.
 *
 * <p>The fix: implement {@link JsonMapperBuilderCustomizer} and let Boot
 * call it while building the mapper it actually wires everywhere.
 */
@Configuration(proxyBeanMethods = false)
public class ObjectMapperConfig {

    @Bean
    public JsonMapperBuilderCustomizer moneyModuleCustomizer() {
        return (JsonMapper.Builder builder) -> builder
                .addModule(new MoneyModule())
                // Write Instant as an ISO-8601 string, matching jackson2-example's
                // WRITE_DATES_AS_TIMESTAMPS override for an apples-to-apples diff.
                //
                // Migration note: this feature moved from the JSON-agnostic
                // SerializationFeature enum (Jackson 2) to the java.time-specific
                // DateTimeFeature enum (Jackson 3, a DatatypeFeature).
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
