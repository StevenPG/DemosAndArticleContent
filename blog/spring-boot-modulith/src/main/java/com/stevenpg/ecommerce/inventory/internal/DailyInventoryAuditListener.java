package com.stevenpg.ecommerce.inventory.internal;

import com.stevenpg.ecommerce.inventory.LowStockWarningEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.stereotype.Component;

/**
 * Runs a daily inventory audit triggered by the Spring Modulith Moments module.
 *
 * When the system clock crosses midnight, {@code spring-modulith-moments} fires a
 * {@link DayHasPassed} event.  This listener wakes up in its own AFTER_COMMIT
 * transaction (courtesy of {@code @ApplicationModuleListener}) and inspects every
 * inventory item.  Any item whose available quantity falls below the low-stock
 * threshold causes a {@link LowStockWarningEvent} to be published so that
 * downstream modules (e.g. a future procurement or notification module) can react.
 *
 * This pattern — using Moments time events to trigger periodic domain logic —
 * replaces cron-scheduled beans with a fully event-driven, module-aware approach.
 * Because the listener participates in the event publication registry, the audit
 * is retried on restart if the previous attempt failed mid-flight.
 */
@Component
class DailyInventoryAuditListener {

    private static final Logger log = LoggerFactory.getLogger(DailyInventoryAuditListener.class);
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final InventoryItemRepository repository;
    private final ApplicationEventPublisher events;

    DailyInventoryAuditListener(InventoryItemRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @ApplicationModuleListener
    void onDayHasPassed(DayHasPassed event) {
        var items = repository.findAll();

        log.info("Daily inventory audit [{}]: checking {} items", event.getDate(), items.size());

        items.stream()
            .filter(item -> item.available() < LOW_STOCK_THRESHOLD)
            .forEach(item -> {
                log.warn("Low stock warning: product {} has only {} unit(s) available",
                    item.getProductId(), item.available());
                events.publishEvent(new LowStockWarningEvent(item.getProductId(), item.available()));
            });
    }
}
