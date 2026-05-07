package com.stevenpg.ecommerce.inventory.internal;

import com.stevenpg.ecommerce.inventory.StockReservedEvent;
import com.stevenpg.ecommerce.inventory.StockShortageEvent;
import com.stevenpg.ecommerce.orders.OrderPlacedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class OrderPlacedListener {

    private final InventoryItemRepository repository;
    private final ApplicationEventPublisher events;

    OrderPlacedListener(InventoryItemRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @ApplicationModuleListener
    void onOrderPlaced(OrderPlacedEvent event) {
        for (var item : event.items()) {
            var stock = repository.findByProductId(item.productId()).orElse(null);
            if (stock == null || !stock.reserve(item.quantity())) {
                events.publishEvent(new StockShortageEvent(event.orderId(), item.productId()));
                return;
            }
            repository.save(stock);
        }
        events.publishEvent(new StockReservedEvent(event.orderId(), event.totalAmount()));
    }
}
