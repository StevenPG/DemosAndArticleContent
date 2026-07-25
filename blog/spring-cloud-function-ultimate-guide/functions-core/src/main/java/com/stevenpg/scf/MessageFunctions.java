package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Function;

/**
 * Working with the {@link Message} envelope instead of the bare payload.
 *
 * <p>When a function's input type is {@code Message<T>}, Spring Cloud Function
 * still converts the payload to {@code T} for you (from JSON, CSV, whatever the
 * {@code contentType} says) but ALSO hands you the headers. When the output is a
 * {@code Message<T>}, the headers you set travel onward — over HTTP they become
 * response headers, over Kafka they become record headers. Same function, both
 * surfaces.
 */
@Configuration
public class MessageFunctions {

    /**
     * Reads an inbound {@code channel} header, runs the decision, and echoes the
     * channel back plus a couple of derived headers. This is the idiomatic way
     * to carry correlation IDs / tenant IDs / trace context through a function.
     */
    @Bean
    public Function<Message<Order>, Message<Decision>> decideWithHeaders() {
        return message -> {
            Order order = message.getPayload();
            Object channel = message.getHeaders().getOrDefault("channel", "unknown");

            EnrichedOrder enriched = EnrichedOrder.from(order,
                    PipelineFunctions.tierFor(order.customerId()),
                    PipelineFunctions.riskScore(order));
            Decision decision = PipelineFunctions.decide(enriched);

            return MessageBuilder.withPayload(decision)
                    .setHeader("channel", channel)
                    .setHeader("decision-outcome", decision.outcome())
                    .setHeader("customer-tier", decision.customerTier())
                    .setHeader("processed-by", "decideWithHeaders")
                    .build();
        };
    }
}
