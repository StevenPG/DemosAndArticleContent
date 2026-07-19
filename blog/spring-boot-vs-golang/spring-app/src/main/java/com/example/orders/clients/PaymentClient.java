package com.example.orders.clients;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * First outbound OAuth2 target. The injected RestClient already carries the
 * "payment" client-credentials interceptor, so this class is pure HTTP logic.
 */
@Component
public class PaymentClient {

    public record PaymentResult(String paymentId, String status) {
    }

    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public PaymentResult charge(UUID orderId, long amountCents) {
        return restClient.post()
                .uri("/payments")
                .body(Map.of("orderId", orderId.toString(), "amountCents", amountCents))
                .retrieve()
                .body(PaymentResult.class);
    }
}
