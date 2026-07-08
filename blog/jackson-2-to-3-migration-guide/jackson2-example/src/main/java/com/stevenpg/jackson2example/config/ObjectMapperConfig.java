package com.stevenpg.jackson2example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stevenpg.jackson2example.json.MoneyModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds this application's {@link ObjectMapper} EXPLICITLY rather than
 * relying on Spring Boot's autoconfigured one.
 *
 * <p>Why explicit? Spring Boot's default {@code ObjectMapper} bean already
 * auto-detects {@code jackson-datatype-jsr310} and {@code jackson-datatype-jdk8}
 * on the classpath and registers them for you (via
 * {@code Jackson2ObjectMapperBuilder.findModulesViaServiceLoader(true)}),
 * which would quietly hide the exact differences this project exists to
 * show. Building the mapper by hand makes every required step visible.
 *
 * <p><b>Migration note - mutable vs immutable:</b> {@link ObjectMapper} in
 * Jackson 2 is a MUTABLE object: {@code new ObjectMapper()} gives you a
 * default instance, and you bolt features onto it afterwards by calling
 * dozens of instance methods - {@code registerModule(...)},
 * {@code configure(...)}, {@code enable(...)}, {@code disable(...)} - all of
 * which mutate that same instance in place and are still callable at any
 * time, even after the mapper has been handed out to callers and started
 * receiving traffic. Nothing stops two threads from racing a
 * {@code mapper.configure(...)} call against `mapper.writeValueAsString(...)`
 * calls on other threads.
 *
 * <p>Jackson 3's {@code ObjectMapper} has NONE of those mutator methods.
 * Configuration only happens through {@code JsonMapper.builder()...build()};
 * once built, the mapper is immutable for its entire lifetime - "configure
 * once, use forever" is enforced by the type system, not just convention.
 * See jackson3-example's {@code ObjectMapperConfig} for the builder
 * equivalent of every line below.
 */
@Configuration(proxyBeanMethods = false)
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Each of these mutates `mapper` in place and returns `this` for chaining -
        // convenient, but it means the mapper is never "finished" being configured.
        mapper.registerModule(new JavaTimeModule());   // java.time.* support (see BlogPost.publishedAt)
        mapper.registerModule(new Jdk8Module());       // java.util.Optional support (see Author.twitterHandle)
        mapper.registerModule(new MoneyModule());      // our custom Money (de)serializer

        // Write Instant as an ISO-8601 string ("2026-01-15T10:30:00Z") instead
        // of Jackson's numeric-epoch default - the human-readable format most
        // REST APIs actually want.
        //
        // Migration note: this exact enum constant moves in Jackson 3 - from
        // the JSON-agnostic SerializationFeature (where it sits next to
        // unrelated settings like FAIL_ON_EMPTY_BEANS) to a new
        // java.time-specific DateTimeFeature enum. See jackson3-example's
        // ObjectMapperConfig for the replacement call.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
