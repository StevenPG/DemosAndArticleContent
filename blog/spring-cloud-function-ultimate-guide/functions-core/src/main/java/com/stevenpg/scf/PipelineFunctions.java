package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The imperative order/payment pipeline, expressed as plain
 * {@link java.util.function} beans.
 *
 * <p>This is the whole point of Spring Cloud Function: none of these beans know
 * anything about HTTP, Kafka, RSocket or Lambda. They are ordinary functions.
 * Every {@code app-*} / {@code adapter-*} module picks them up from the
 * {@code FunctionCatalog} and exposes them over a surface.
 *
 * <p>Each bean name is the name you use in
 * {@code spring.cloud.function.definition} to select or compose it, e.g.
 * {@code enrichOrder|validateOrder}.
 */
@Configuration
public class PipelineFunctions {

    private static final Logger LOG = System.getLogger(PipelineFunctions.class.getName());

    // -----------------------------------------------------------------------
    // Supplier — a *source*. Something that produces values with no input.
    // The web adapter maps it to `GET /generateOrders`; the stream adapter can
    // poll it on a schedule and publish each result to Kafka. Same bean.
    // -----------------------------------------------------------------------
    @Bean
    public Supplier<Order> generateOrders() {
        AtomicLong seq = new AtomicLong();
        String[] customers = {"cust-alice", "cust-bob", "cust-carol", "cust-dave"};
        return () -> {
            long n = seq.incrementAndGet();
            String customer = customers[(int) (n % customers.length)];
            BigDecimal amount = BigDecimal.valueOf(50L + (n * 37L) % 15000L);
            int items = 1 + (int) (n % 5);
            Order order = new Order("ord-" + n, customer, amount, "USD", items);
            LOG.log(Level.DEBUG, "generateOrders -> {0}", order);
            return order;
        };
    }

    // -----------------------------------------------------------------------
    // Function — the classic transform. Order in, EnrichedOrder out.
    // Adds a customer tier and a computed risk score that later stages need.
    // -----------------------------------------------------------------------
    @Bean
    public Function<Order, EnrichedOrder> enrichOrder() {
        return order -> {
            String tier = tierFor(order.customerId());
            int risk = riskScore(order);
            EnrichedOrder enriched = EnrichedOrder.from(order, tier, risk);
            LOG.log(Level.DEBUG, "enrichOrder -> {0}", enriched);
            return enriched;
        };
    }

    // -----------------------------------------------------------------------
    // Function — the decision step. This is the natural composition partner of
    // enrichOrder: `enrichOrder|validateOrder` is a Function<Order, Decision>.
    // -----------------------------------------------------------------------
    @Bean
    public Function<EnrichedOrder, Decision> validateOrder() {
        return enriched -> {
            Decision decision = decide(enriched);
            LOG.log(Level.DEBUG, "validateOrder -> {0}", decision);
            return decision;
        };
    }

    // -----------------------------------------------------------------------
    // Consumer — a *sink*. Something that takes a value and returns nothing.
    // Compose it onto the end for a fire-and-forget pipeline:
    // `enrichOrder|validateOrder|notify` is a Consumer<Order>.
    // -----------------------------------------------------------------------
    @Bean("notify")
    public Consumer<Decision> notifyOutcome() {
        return decision -> LOG.log(Level.INFO,
                "notify -> order {0} was {1} ({2})",
                decision.orderId(), decision.outcome(), decision.reason());
    }

    // -----------------------------------------------------------------------
    // A function over a COLLECTION. SCF happily maps List<T> in / List<T> out,
    // which is how you batch on the messaging surfaces.
    // -----------------------------------------------------------------------
    @Bean
    public Function<List<Order>, List<Decision>> validateBatch() {
        Function<Order, EnrichedOrder> enrich = enrichOrder();
        Function<EnrichedOrder, Decision> validate = validateOrder();
        return orders -> orders.stream().map(enrich).map(validate).toList();
    }

    // ---- shared business rules (deterministic, so tests are stable) --------

    static String tierFor(String customerId) {
        int h = Math.abs(customerId.hashCode()) % 3;
        return switch (h) {
            case 0 -> "PLATINUM";
            case 1 -> "GOLD";
            default -> "STANDARD";
        };
    }

    static int riskScore(Order order) {
        int base = order.amount().intValue() / 200;         // pricier -> riskier
        int itemsFactor = order.itemCount() * 5;
        return Math.min(100, base + itemsFactor);
    }

    static Decision decide(EnrichedOrder e) {
        if (e.riskScore() >= 70) {
            return new Decision(e.orderId(), Decision.REVIEW,
                    "risk score " + e.riskScore() + " needs manual review", e.amount(), e.customerTier());
        }
        boolean bigSpender = e.amount().compareTo(BigDecimal.valueOf(10_000)) > 0;
        if (bigSpender && !"PLATINUM".equals(e.customerTier())) {
            return new Decision(e.orderId(), Decision.REJECTED,
                    "amount over limit for tier " + e.customerTier(), e.amount(), e.customerTier());
        }
        return new Decision(e.orderId(), Decision.APPROVED,
                "within risk and spend limits", e.amount(), e.customerTier());
    }
}
