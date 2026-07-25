package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.Order;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Function;

/**
 * Function ROUTING — dispatching one input to different functions at runtime.
 *
 * <p>Spring Cloud Function ships a built-in routing function registered under
 * the name {@code functionRouter}. Set {@code spring.cloud.function.definition=functionRouter}
 * and it decides, per message, which real function to invoke. It picks the
 * target in this order of precedence:
 *
 * <ol>
 *   <li>a {@code spring.cloud.function.definition} <em>message header</em>;</li>
 *   <li>the {@code spring.cloud.function.routing-expression} SpEL property
 *       (see the app modules' YAML for an example);</li>
 *   <li>a {@link MessageRoutingCallback} bean — the programmatic hook below.</li>
 * </ol>
 *
 * <p>The callback lets you route on anything you can read off the message. Here
 * we send small "express" orders down a cheap fast-approve path and everything
 * else through the full {@code enrichOrder|validateOrder} pipeline.
 */
@Configuration
public class RoutingConfig {

    /** The cheap path: tiny orders are auto-approved without enrichment. */
    @Bean
    public Function<Order, Decision> fastApprove() {
        return order -> new Decision(order.orderId(), Decision.APPROVED,
                "auto-approved via express fast-lane", order.amount(), "EXPRESS");
    }

    /**
     * Programmatic routing. Returns the function definition string the router
     * should invoke for this message. We route on the {@code order-channel}
     * header so routing works even before the payload is converted to a POJO.
     */
    @Bean
    public MessageRoutingCallback orderRouter() {
        return new MessageRoutingCallback() {
            @Override
            public String routingResult(Message<?> message) {
                Object channel = message.getHeaders().get("order-channel");
                if ("express".equals(channel)) {
                    return "fastApprove";
                }
                // Default: the full, composed pipeline.
                return "enrichOrder|validateOrder";
            }
        };
    }
}
