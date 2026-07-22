package com.example.schemaregistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.schemaregistry.avro.OrderEvent;
import com.example.schemaregistry.avro.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal HTTP surface so you can drive the demo with curl:
 * <pre>
 *   POST /orders           -> publishes an OrderEvent to Kafka (Avro + Schema Registry)
 *   GET  /orders/received  -> shows what the consumer has read back
 * </pre>
 */
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderProducer producer;
    private final OrderEventStore store;

    OrderController(OrderProducer producer, OrderEventStore store) {
        this.producer = producer;
        this.store = store;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> place(@RequestBody PlaceOrderRequest request) {
        OrderEvent event = OrderEvent.newBuilder()
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId(request.customerId())
                .setAmount(request.amount())
                .setCurrency(request.currency() == null ? "USD" : request.currency())
                .setStatus(OrderStatus.PLACED)
                .setCreatedAt(Instant.now())
                .build();

        producer.send(event);

        return ResponseEntity.accepted().body(Map.of(
                "orderId", event.getOrderId(),
                "status", event.getStatus().toString()));
    }

    @GetMapping("/received")
    List<Map<String, Object>> received() {
        return store.all().stream()
                .map(event -> Map.<String, Object>of(
                        "orderId", event.getOrderId(),
                        "customerId", event.getCustomerId(),
                        "amount", event.getAmount(),
                        "currency", event.getCurrency(),
                        "status", event.getStatus().toString(),
                        "createdAt", event.getCreatedAt().toString()))
                .toList();
    }

    record PlaceOrderRequest(String customerId, double amount, String currency) {
    }
}
