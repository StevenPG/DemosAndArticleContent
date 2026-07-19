package com.example.orders.clients;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Second outbound OAuth2 target, using its own "inventory" registration —
 * different client id, secret, and scope than the payment client.
 */
@Component
public class InventoryClient {

    public record ReservationResult(String reservationId, String status) {
    }

    private final RestClient restClient;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ReservationResult reserve(UUID orderId, String item, int quantity) {
        return restClient.post()
                .uri("/inventory/reservations")
                .body(Map.of(
                        "orderId", orderId.toString(),
                        "item", item,
                        "quantity", quantity))
                .retrieve()
                .body(ReservationResult.class);
    }
}
