package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the SAME functions-core beans are reachable over HTTP with no
 * controller code — including ad-hoc composition via a comma in the path and
 * header-driven routing through the built-in {@code functionRouter}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebApplicationTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static Order sampleOrder() {
        return new Order("ord-1", "cust-alice", new BigDecimal("199.99"), "USD", 2);
    }

    @Test
    void enrichOrderEndpointReturnsEnrichedOrder() {
        EnrichedOrder result = client().post().uri("/enrichOrder")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleOrder())
                .exchange()
                .expectStatus().isOk()
                .expectBody(EnrichedOrder.class)
                .returnResult().getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo("ord-1");
        assertThat(result.customerTier()).isNotBlank();
    }

    @Test
    void composedPipelineEndpointReturnsDecision() {
        Decision decision = client().post().uri("/enrichOrder,validateOrder")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sampleOrder())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Decision.class)
                .returnResult().getResponseBody();

        assertThat(decision).isNotNull();
        assertThat(decision.orderId()).isEqualTo("ord-1");
        assertThat(decision.outcome()).isIn(Decision.APPROVED, Decision.REJECTED, Decision.REVIEW);
    }

    @Test
    void acceptsCustomCsvContentType() {
        // The custom CsvOrderMessageConverter lets the SAME enrichOrder function
        // be invoked with text/csv instead of JSON.
        EnrichedOrder result = client().post().uri("/enrichOrder")
                .contentType(MediaType.valueOf("text/csv"))
                .bodyValue("ord-9,cust-alice,199.99,USD,3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(EnrichedOrder.class)
                .returnResult().getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo("ord-9");
        assertThat(result.itemCount()).isEqualTo(3);
    }

    @Test
    void routerSendsExpressChannelToFastApprove() {
        Decision decision = client().post().uri("/functionRouter")
                .contentType(MediaType.APPLICATION_JSON)
                .header("order-channel", "express")
                .bodyValue(new Order("ord-x", "cust-bob", new BigDecimal("25.00"), "USD", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Decision.class)
                .returnResult().getResponseBody();

        assertThat(decision).isNotNull();
        assertThat(decision.outcome()).isEqualTo(Decision.APPROVED);
        assertThat(decision.reason()).contains("express");
    }
}
