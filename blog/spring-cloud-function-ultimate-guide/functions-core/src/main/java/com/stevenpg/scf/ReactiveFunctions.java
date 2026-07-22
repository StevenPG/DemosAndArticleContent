package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reactive (Flux/Mono) variants of the same pipeline, side by side with the
 * imperative ones in {@link PipelineFunctions}.
 *
 * <p>Spring Cloud Function treats {@code Function<Order, Decision>} and
 * {@code Function<Flux<Order>, Flux<Decision>>} uniformly: it will adapt an
 * imperative function to a reactive stream and vice-versa, so an adapter can
 * call either style. You reach for the reactive form when you want to work on
 * the stream itself — buffering, windowing, backpressure, async fan-out.
 *
 * <p>Reactive Suppliers are special: a {@code Supplier<Flux<T>>} is treated as
 * an unbounded producer. The stream adapter turns one into a continuous source
 * of Kafka records with no polling configuration at all.
 */
@Configuration
public class ReactiveFunctions {

    /**
     * The whole pipeline as a single reactive function. Note we operate on the
     * {@code Flux} — this is where you'd add {@code .buffer()},
     * {@code .window()}, {@code .flatMap(this::callAsyncService)}, etc.
     */
    @Bean
    public Function<Flux<Order>, Flux<Decision>> reactivePipeline() {
        return orders -> orders
                .map(o -> EnrichedOrder.from(o,
                        PipelineFunctions.tierFor(o.customerId()),
                        PipelineFunctions.riskScore(o)))
                .map(PipelineFunctions::decide);
    }

    /**
     * A reactive enrichment step, so you can compose reactive-with-reactive:
     * {@code reactiveEnrich|reactiveValidate}.
     */
    @Bean
    public Function<Flux<Order>, Flux<EnrichedOrder>> reactiveEnrich() {
        return orders -> orders.map(o -> EnrichedOrder.from(o,
                PipelineFunctions.tierFor(o.customerId()),
                PipelineFunctions.riskScore(o)));
    }

    @Bean
    public Function<Flux<EnrichedOrder>, Flux<Decision>> reactiveValidate() {
        return enriched -> enriched.map(PipelineFunctions::decide);
    }

    /**
     * A reactive Supplier — an unbounded source that emits one synthetic order
     * per second. Point a stream binding at {@code liveOrders} and you get a
     * self-driving producer; there is no {@code poller} config involved.
     */
    @Bean
    public Supplier<Flux<Order>> liveOrders() {
        Supplier<Order> imperative = new PipelineFunctions().generateOrders();
        return () -> Flux.interval(Duration.ofSeconds(1)).map(tick -> imperative.get());
    }
}
