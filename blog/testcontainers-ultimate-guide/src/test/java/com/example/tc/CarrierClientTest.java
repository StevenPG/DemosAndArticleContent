package com.example.tc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The external carrier API mocked with the official WireMock Testcontainers
 * module. Stubs live in src/test/resources/wiremock/ as plain JSON files.
 */
@SpringBootTest
@Import(ContainersConfig.class)
@Testcontainers
class CarrierClientTest {

    @Container
    static WireMockContainer wiremock =
            new WireMockContainer("wiremock/wiremock:3.13.0")
                    .withMappingFromResource("carrier-track.json");

    @DynamicPropertySource
    static void carrierUrl(DynamicPropertyRegistry registry) {
        registry.add("carrier.base-url", wiremock::getBaseUrl);
    }

    @Autowired
    CarrierClient carrierClient;

    @Test
    void parsesCarrierResponse() {
        CarrierClient.CarrierStatus status = carrierClient.track("TRACK-999");

        assertThat(status.trackingNumber()).isEqualTo("TRACK-999");
        assertThat(status.status()).isEqualTo("IN_TRANSIT");
    }
}
