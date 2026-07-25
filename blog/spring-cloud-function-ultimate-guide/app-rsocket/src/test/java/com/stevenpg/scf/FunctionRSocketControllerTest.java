package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.rsocket.RSocketRequester;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connects a real RSocket client to the running server and drives the same
 * functions-core beans over RSocket's request/response and request/channel
 * interaction models.
 */
@SpringBootTest(properties = "spring.rsocket.server.port=7001")
class FunctionRSocketControllerTest {

    @Autowired
    RSocketRequester.Builder builder;

    private RSocketRequester requester;

    @BeforeEach
    void connect() {
        requester = builder.tcp("localhost", 7001);
    }

    @AfterEach
    void disconnect() {
        if (requester != null) {
            requester.dispose();
        }
    }

    private static Order order(String id, String amount) {
        return new Order(id, "cust-alice", new BigDecimal(amount), "USD", 2);
    }

    @Test
    void requestResponseEnrich() {
        EnrichedOrder enriched = requester.route("orders.enrich")
                .data(order("ord-1", "199.99"))
                .retrieveMono(EnrichedOrder.class)
                .block();

        assertThat(enriched).isNotNull();
        assertThat(enriched.orderId()).isEqualTo("ord-1");
        assertThat(enriched.customerTier()).isNotBlank();
    }

    @Test
    void requestResponseDecide() {
        Decision decision = requester.route("orders.decide")
                .data(order("ord-2", "250.00"))
                .retrieveMono(Decision.class)
                .block();

        assertThat(decision).isNotNull();
        assertThat(decision.orderId()).isEqualTo("ord-2");
        assertThat(decision.outcome()).isIn(Decision.APPROVED, Decision.REJECTED, Decision.REVIEW);
    }

    @Test
    void requestChannelStream() {
        Flux<Order> orders = Flux.just(order("ord-a", "120"), order("ord-b", "340"));

        Flux<Decision> decisions = requester.route("orders.decideStream")
                .data(orders)
                .retrieveFlux(Decision.class);

        StepVerifier.create(decisions)
                .assertNext(d -> assertThat(d.orderId()).isEqualTo("ord-a"))
                .assertNext(d -> assertThat(d.orderId()).isEqualTo("ord-b"))
                .verifyComplete();
    }
}
