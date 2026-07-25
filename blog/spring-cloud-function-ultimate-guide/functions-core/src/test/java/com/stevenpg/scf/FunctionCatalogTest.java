package com.stevenpg.scf;

import com.stevenpg.scf.model.Decision;
import com.stevenpg.scf.model.EnrichedOrder;
import com.stevenpg.scf.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the beans in {@code functions-core} are discoverable and invocable
 * through the {@link FunctionCatalog} exactly as every adapter module will call
 * them — including composition, {@code Message} handling, reactive types,
 * multi-output tuples, and the function registered at runtime.
 */
class FunctionCatalogTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContextFunctionCatalogAutoConfiguration.class))
            .withUserConfiguration(
                    PipelineFunctions.class,
                    ReactiveFunctions.class,
                    MessageFunctions.class,
                    TupleFunctions.class,
                    RoutingConfig.class,
                    ConverterConfig.class,
                    DynamicFunctionRegistrar.class);

    private static Order sampleOrder() {
        return new Order("ord-1", "cust-alice", new BigDecimal("199.99"), "USD", 2);
    }

    @Test
    void composesEnrichAndValidate() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);

            // The '|' composition operator is resolved by the catalog itself.
            Function<Order, Decision> pipeline = catalog.lookup("enrichOrder|validateOrder");
            assertThat(pipeline).isNotNull();

            Decision decision = pipeline.apply(sampleOrder());
            assertThat(decision.orderId()).isEqualTo("ord-1");
            assertThat(decision.outcome()).isIn(Decision.APPROVED, Decision.REJECTED, Decision.REVIEW);
        });
    }

    @Test
    void enrichesThenExposesTierAndRisk() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
            Function<Order, EnrichedOrder> enrich = catalog.lookup("enrichOrder");

            EnrichedOrder enriched = enrich.apply(sampleOrder());
            assertThat(enriched.customerTier()).isNotBlank();
            assertThat(enriched.riskScore()).isBetween(0, 100);
        });
    }

    @Test
    void carriesMessageHeadersThrough() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
            Function<Message<Order>, Message<Decision>> fn = catalog.lookup("decideWithHeaders");

            Message<Order> input = MessageBuilder.withPayload(sampleOrder())
                    .setHeader("channel", "mobile-app")
                    .build();
            Message<Decision> output = fn.apply(input);

            assertThat(output.getPayload().orderId()).isEqualTo("ord-1");
            assertThat(output.getHeaders().get("channel")).isEqualTo("mobile-app");
            assertThat(output.getHeaders().get("processed-by")).isEqualTo("decideWithHeaders");
        });
    }

    @Test
    void runsReactivePipeline() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
            Function<Flux<Order>, Flux<Decision>> fn = catalog.lookup("reactivePipeline");

            StepVerifier.create(fn.apply(Flux.just(sampleOrder())))
                    .assertNext(d -> assertThat(d.orderId()).isEqualTo("ord-1"))
                    .verifyComplete();
        });
    }

    @Test
    void fansOutWithMultiOutputTuple() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
            Function<Flux<Order>, Tuple2<Flux<Decision>, Flux<Decision>>> fn =
                    catalog.lookup("partitionDecisions");

            Order small = new Order("ord-a", "cust-alice", new BigDecimal("120"), "USD", 1);
            Tuple2<Flux<Decision>, Flux<Decision>> outputs = fn.apply(Flux.just(small));

            // partitionDecisions uses publish().autoConnect(2): BOTH outputs must
            // be subscribed before the upstream connects (exactly what a Kafka
            // binder does with two output topics). Mono.zip subscribes to both.
            StepVerifier.create(Mono.zip(
                            outputs.getT1().collectList(),
                            outputs.getT2().collectList()))
                    .assertNext(both -> {
                        assertThat(both.getT1())
                                .as("approved output")
                                .hasSize(1)
                                .allMatch(d -> Decision.APPROVED.equals(d.outcome()));
                        assertThat(both.getT2()).as("needs-attention output").isEmpty();
                    })
                    .verifyComplete();
        });
    }

    @Test
    void invokesFunctionRegisteredAtRuntime() {
        runner.run(context -> {
            FunctionCatalog catalog = context.getBean(FunctionCatalog.class);
            Function<String, String> dynamic = catalog.lookup("dynamicUppercase");

            assertThat(dynamic).as("dynamicUppercase should have been registered at startup").isNotNull();
            assertThat(dynamic.apply("hello")).isEqualTo("HELLO");
        });
    }
}
