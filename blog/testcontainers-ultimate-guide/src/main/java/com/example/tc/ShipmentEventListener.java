package com.example.tc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes "tracking:status" messages and updates the shipment row —
 * exactly the kind of cross-service glue that unit tests can't cover and
 * Testcontainers can.
 */
@Component
public class ShipmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventListener.class);

    private final ShipmentRepository repository;

    public ShipmentEventListener(ShipmentRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "shipment-events", groupId = "tc-demo")
    @Transactional
    public void onEvent(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            log.warn("Ignoring malformed event: {}", message);
            return;
        }
        repository.findByTrackingNumber(parts[0]).ifPresent(shipment -> {
            shipment.setStatus(parts[1]);
            repository.save(shipment);
            log.info("Shipment {} -> {}", parts[0], parts[1]);
        });
    }
}
