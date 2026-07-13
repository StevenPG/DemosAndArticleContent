package com.example.tc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the external carrier's REST API for live tracking status. In tests
 * this URL points at a WireMock container.
 */
@Component
public class CarrierClient {

    private final RestClient restClient;

    public CarrierClient(RestClient.Builder builder,
                         @Value("${carrier.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public record CarrierStatus(String trackingNumber, String status) {
    }

    public CarrierStatus track(String trackingNumber) {
        return restClient.get()
                .uri("/v1/track/{trackingNumber}", trackingNumber)
                .retrieve()
                .body(CarrierStatus.class);
    }
}
