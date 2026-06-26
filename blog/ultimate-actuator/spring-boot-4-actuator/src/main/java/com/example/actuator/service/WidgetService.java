package com.example.actuator.service;

import com.example.actuator.domain.Widget;
import com.example.actuator.domain.WidgetRepository;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Business service that deliberately demonstrates several Actuator-adjacent ideas
 * at once:
 *
 * <ul>
 *     <li>{@link Counted}/{@link Timed} &mdash; declarative Micrometer metrics created
 *         purely with annotations (backed by the {@code CountedAspect}/{@code TimedAspect}
 *         beans registered in {@code ObservabilityConfig}).</li>
 *     <li>A hand-rolled {@link Counter} &mdash; the imperative style of metric creation.</li>
 *     <li>{@link Observed} &mdash; produces both a metric <em>and</em> a distributed trace
 *         span via the Micrometer Observation API.</li>
 *     <li>{@link Cacheable}/{@link CacheEvict} &mdash; feeds the {@code caches} endpoint and
 *         the Caffeine cache metrics.</li>
 *     <li>{@link AuditApplicationEvent} &mdash; publishes custom audit events that surface in
 *         the {@code auditevents} endpoint.</li>
 * </ul>
 */
@Slf4j
@Service
public class WidgetService {

    private final WidgetRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    /** Imperatively-created counter: incremented every time a widget is created. */
    private final Counter widgetsCreatedCounter;

    public WidgetService(WidgetRepository repository,
                         ApplicationEventPublisher eventPublisher,
                         MeterRegistry meterRegistry) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.widgetsCreatedCounter = Counter.builder("widgets.created")
                .description("Total number of widgets created since startup")
                .baseUnit("widgets")
                .tag("source", "service")
                .register(meterRegistry);
    }

    /**
     * Reads are cached so that the {@code caches} actuator endpoint has live data,
     * and so cache hit/miss metrics ({@code cache.gets}) become meaningful.
     */
    @Cacheable("widgets")
    @Observed(name = "widget.lookup", contextualName = "widget-lookup")
    public Widget findById(Long id) {
        log.info("Cache miss - loading widget {} from the database", id);
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No widget with id " + id));
    }

    @Timed(value = "widgets.list", description = "Time spent listing all widgets", histogram = true)
    @Observed(name = "widget.list")
    public List<Widget> findAll() {
        return repository.findAll();
    }

    @Counted(value = "widgets.create.attempts", description = "Number of create-widget invocations")
    @Observed(name = "widget.create")
    @CacheEvict(value = "widgets", allEntries = true)
    public Widget create(String name, String color) {
        Widget saved = repository.save(Widget.builder().name(name).color(color).build());
        widgetsCreatedCounter.increment();
        publishAuditEvent("WIDGET_CREATED", Map.of("id", saved.getId(), "name", name));
        return saved;
    }

    @CacheEvict(value = "widgets", key = "#id")
    public void delete(Long id) {
        repository.deleteById(id);
        publishAuditEvent("WIDGET_DELETED", Map.of("id", id));
    }

    public long count() {
        return repository.count();
    }

    /**
     * Publishes a Spring Boot {@link AuditApplicationEvent}. These are stored by the
     * {@code AuditEventRepository} bean and exposed at {@code /actuator/auditevents}.
     */
    private void publishAuditEvent(String type, Map<String, Object> data) {
        String principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "anonymous";
        eventPublisher.publishEvent(new AuditApplicationEvent(new AuditEvent(principal, type, data)));
    }
}
