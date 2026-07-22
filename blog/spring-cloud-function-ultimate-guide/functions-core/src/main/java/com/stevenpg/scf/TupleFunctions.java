package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.function.Function;

/**
 * Multi-arity functions: more than one input or more than one output.
 *
 * <p>A normal {@code Function} is one-in / one-out. Spring Cloud Function also
 * supports many-in and many-out by using Reactor's {@link Tuple2} of
 * {@code Flux}. On a messaging binder each element of the tuple maps to its own
 * binding (its own topic), which is how you fan-in from several topics or fan-out
 * to several topics from a single function.
 */
@Configuration
public class TupleFunctions {

    /**
     * MULTI-OUTPUT (fan-out): one stream of orders in, TWO streams of decisions
     * out — approved on one output, everything needing attention on the other.
     * On Kafka these become two separate output topics.
     */
    @Bean
    public Function<Flux<Order>, Tuple2<Flux<Decision>, Flux<Decision>>> partitionDecisions() {
        return orders -> {
            // publish().autoConnect(2) lets both downstream flows share one
            // upstream subscription instead of running the pipeline twice.
            Flux<Decision> decisions = orders
                    .map(o -> EnrichedOrder.from(o,
                            PipelineFunctions.tierFor(o.customerId()),
                            PipelineFunctions.riskScore(o)))
                    .map(PipelineFunctions::decide)
                    .publish()
                    .autoConnect(2);

            Flux<Decision> approved = decisions.filter(d -> Decision.APPROVED.equals(d.outcome()));
            Flux<Decision> needsAttention = decisions.filter(d -> !Decision.APPROVED.equals(d.outcome()));
            return Tuples.of(approved, needsAttention);
        };
    }

    /**
     * MULTI-INPUT (fan-in): a stream of orders zipped with a parallel stream of
     * priority flags, joined into a single decision stream. On Kafka these are
     * two input topics feeding one function.
     */
    @Bean
    public Function<Tuple2<Flux<Order>, Flux<String>>, Flux<Decision>> joinWithPriority() {
        return tuple -> {
            Flux<Order> orders = tuple.getT1();
            Flux<String> priorities = tuple.getT2();
            return orders.zipWith(priorities, (order, priority) -> {
                EnrichedOrder enriched = EnrichedOrder.from(order,
                        PipelineFunctions.tierFor(order.customerId()),
                        PipelineFunctions.riskScore(order));
                Decision base = PipelineFunctions.decide(enriched);
                // "high" priority forces a manual review regardless of the score.
                if ("high".equalsIgnoreCase(priority) && Decision.APPROVED.equals(base.outcome())) {
                    return new Decision(base.orderId(), Decision.REVIEW,
                            "high-priority manual review requested", base.amount(), base.customerTier());
                }
                return base;
            });
        };
    }
}
