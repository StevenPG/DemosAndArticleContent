package com.stevenpg.ecommerce.payments.internal;

import com.stevenpg.ecommerce.inventory.StockReservedEvent;
import com.stevenpg.ecommerce.payments.PaymentService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StockReservedListener {

    private final PaymentService payments;

    StockReservedListener(PaymentService payments) {
        this.payments = payments;
    }

    @ApplicationModuleListener
    void onStockReserved(StockReservedEvent event) {
        payments.process(event.orderId(), event.orderTotal());
    }
}
