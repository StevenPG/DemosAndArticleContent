package com.example.actuator.config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
// Spring Boot 4: MeterRegistryCustomizer moved to the new micrometer-metrics module.
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central wiring for metrics, tracing and audit infrastructure. Each bean here
 * "switches on" a specific Actuator capability.
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Adds a common tag to every single metric emitted by the app. Great for
     * distinguishing instances/regions when scraping into Prometheus/Grafana.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config().commonTags(
                "application", "ultimate-actuator",
                "spring.boot.version", "4");
    }

    /**
     * A {@link MeterFilter} that caps tag cardinality and configures client-side
     * percentiles/SLOs for our HTTP timer. MeterFilters are the supported way to
     * post-process metrics before they reach a registry.
     */
    @Bean
    public MeterFilter httpRequestsMeterFilter() {
        return new MeterFilter() {
            @Override
            public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {
                if (id.getName().startsWith("http.server.requests")) {
                    return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                            .percentiles(0.5, 0.95, 0.99)
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }

    /** Enables the {@code @Timed} annotation used in {@code WidgetService}. */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /** Enables the {@code @Counted} annotation used in {@code WidgetService}. */
    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }

    /** Enables the {@code @Observed} annotation (metrics + trace spans). */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Backing store for the {@code httpexchanges} endpoint. Without this bean the
     * endpoint reports nothing &mdash; Spring Boot deliberately requires you to opt
     * in to recording request/response history.
     */
    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(100);
        return repository;
    }

    /**
     * Backing store for the {@code auditevents} endpoint. We publish our own
     * {@code WIDGET_CREATED}/{@code WIDGET_DELETED} events into it from the service
     * layer, alongside the authentication events Spring Security emits automatically.
     */
    @Bean
    public AuditEventRepository auditEventRepository() {
        return new InMemoryAuditEventRepository(200);
    }
}
