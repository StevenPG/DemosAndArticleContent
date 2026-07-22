package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * The one function this app binds to Kafka.
 *
 * <p>It does no new business logic — it just wires the two existing
 * {@code functions-core} beans together into a single {@code Function<Order,
 * Decision>} that Spring Cloud Stream can bind as {@code orders -> decisions}.
 * The only thing added is an input guard: an order in an unsupported currency
 * throws, which (with {@code enable-dlq: true}) routes that record to the
 * dead-letter topic instead of blocking the partition. That is the canonical
 * "poison message" handling pattern on a binder.
 */
@Configuration
public class StreamBindings {

    @Bean
    public Function<Order, Decision> orderPipeline(
            @Qualifier("enrichOrder") Function<Order, EnrichedOrder> enrichOrder,
            @Qualifier("validateOrder") Function<EnrichedOrder, Decision> validateOrder) {

        Function<Order, Decision> pipeline = enrichOrder.andThen(validateOrder);

        return order -> {
            if (order.currency() == null || !"USD".equals(order.currency())) {
                // Unrecoverable for this consumer -> becomes a dead-letter record.
                throw new IllegalArgumentException("unsupported currency: " + order.currency());
            }
            return pipeline.apply(order);
        };
    }
}
