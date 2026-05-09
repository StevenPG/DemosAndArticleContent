package com.stevenpg.ecommerce.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Place and manage orders")
class OrderController {

    private final OrderManagement management;

    OrderController(OrderManagement management) {
        this.management = management;
    }

    @PostMapping
    @Operation(summary = "Place a new order")
    ResponseEntity<Order> placeOrder(@Valid @RequestBody PlaceOrderCommand command, UriComponentsBuilder ucb) {
        Order order = management.placeOrder(command);
        var location = ucb.path("/api/orders/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        return management.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order")
    ResponseEntity<Void> cancelOrder(@PathVariable UUID id) {
        management.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
