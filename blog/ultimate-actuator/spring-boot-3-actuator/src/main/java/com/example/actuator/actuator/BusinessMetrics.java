package com.example.actuator.actuator;

import com.example.actuator.service.WidgetService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * A {@link MeterBinder} &mdash; the idiomatic way to register metrics that depend on
 * application state. Spring Boot calls {@link #bindTo(MeterRegistry)} for every
 * registry, so the same gauge is correctly published to Prometheus and any other
 * configured registry.
 *
 * <p>This publishes {@code widgets.total}, a live gauge of the row count in the
 * database, which you can graph next to the {@code widgets.created} counter.
 */
@Component
public class BusinessMetrics implements MeterBinder {

    private final WidgetService widgetService;

    public BusinessMetrics(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("widgets.total", widgetService, WidgetService::count)
                .description("Current number of widgets stored in the database")
                .baseUnit("widgets")
                .register(registry);
    }
}
