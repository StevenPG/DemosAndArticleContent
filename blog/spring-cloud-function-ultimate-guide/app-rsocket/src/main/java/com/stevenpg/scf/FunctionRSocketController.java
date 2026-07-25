package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Bridges RSocket routes to Spring Cloud Function.
 *
 * <p>This is the "roll your own adapter" pattern: the {@link FunctionCatalog} is
 * the single source of truth for every function in the app, so exposing it over
 * a new transport is just <em>look up by name, then apply</em>. The same
 * {@code enrichOrder|validateOrder} composition served over HTTP and Kafka is
 * served here with no change to the functions.
 *
 * <p>It also lines up nicely with RSocket's interaction models:
 * <ul>
 *   <li>{@code orders.enrich} / {@code orders.decide} — request/response;</li>
 *   <li>{@code orders.decideStream} — request/channel, using the reactive
 *       pipeline function directly.</li>
 * </ul>
 */
@Controller
public class FunctionRSocketController {

    private final FunctionCatalog catalog;

    public FunctionRSocketController(FunctionCatalog catalog) {
        this.catalog = catalog;
    }

    /** Request/response: one Order in, its EnrichedOrder out. */
    @MessageMapping("orders.enrich")
    public Mono<EnrichedOrder> enrich(Order order) {
        Function<Order, EnrichedOrder> fn = catalog.lookup("enrichOrder");
        return Mono.fromSupplier(() -> fn.apply(order));
    }

    /** Request/response: the full composed pipeline, Order in, Decision out. */
    @MessageMapping("orders.decide")
    public Mono<Decision> decide(Order order) {
        Function<Order, Decision> fn = catalog.lookup("enrichOrder|validateOrder");
        return Mono.fromSupplier(() -> fn.apply(order));
    }

    /** Request/channel: a stream of Orders in, a stream of Decisions out. */
    @MessageMapping("orders.decideStream")
    public Flux<Decision> decideStream(Flux<Order> orders) {
        Function<Flux<Order>, Flux<Decision>> fn = catalog.lookup("reactivePipeline");
        return fn.apply(orders);
    }
}
