package com.example;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@SpringBootApplication
// KafkaAutoConfiguration doesn't activate in Spring Boot 4 without explicit opt-in.
// @EnableKafka enables @KafkaListener processing; we define the factory below.
@EnableKafka
public class Application {

    private static final Logger log = Logger.getLogger(Application.class.getName());

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    RestClient restClient() {
        // Use Java's built-in HTTP client backed by a virtual-thread-per-task executor.
        // This pairs with spring.threads.virtual.enabled=true so every outbound
        // call runs on a virtual thread — no platform thread is held while waiting
        // for the echo server to respond.
        var jdkClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(jdkClient))
                .build();
    }

    // Spring Boot 4's KafkaAutoConfiguration doesn't register kafkaListenerContainerFactory
    // automatically, so we define both the ConsumerFactory and the listener container factory
    // explicitly. The concurrency(5) call here replaces the spring.kafka.listener.concurrency
    // property that auto-configuration would have applied.
    @Bean
    ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG, "spring-consumer-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        // One listener thread per partition. Messages within a partition are delivered
        // one at a time; across partitions work runs concurrently.
        factory.setConcurrency(5);
        return factory;
    }

    // -------------------------------------------------------------------------
    // HTTP API
    // -------------------------------------------------------------------------

    @RestController
    @RequestMapping("/api")
    static class ApiController {

        private final RestClient restClient;

        ApiController(RestClient restClient) {
            this.restClient = restClient;
        }

        @GetMapping("/process")
        ResponseEntity<Map<String, Object>> process() {
            long start = System.currentTimeMillis();

            // With spring.threads.virtual.enabled=true, each HTTP request lands on
            // a virtual thread. This blocking call suspends the virtual thread while
            // waiting for the echo server — the underlying platform thread is freed
            // to run other virtual threads. Equivalent to a goroutine parking on I/O.
            restClient.get()
                    .uri("http://localhost:9000/echo")
                    .retrieve()
                    .toBodilessEntity();

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "elapsed_ms", System.currentTimeMillis() - start
            ));
        }
    }

    // -------------------------------------------------------------------------
    // Kafka Consumer
    // -------------------------------------------------------------------------

    @Component
    static class KafkaMessageConsumer {

        private static final Logger log = Logger.getLogger(KafkaMessageConsumer.class.getName());
        private static final int EXPECTED_MESSAGES = 1000;

        private final RestClient restClient;
        private final AtomicLong processed = new AtomicLong(0);
        private final AtomicLong firstAt   = new AtomicLong(0);
        private final AtomicLong lastAt    = new AtomicLong(0);

        KafkaMessageConsumer(RestClient restClient) {
            this.restClient = restClient;
        }

        // The kafkaListenerContainerFactory (defined above) creates one listener
        // thread per partition. This method is called once per message — it must
        // return before the next record on the same partition is dispatched.
        // Across partitions, the 5 threads run concurrently: up to 5 HTTP calls
        // in flight simultaneously. This mirrors Go's per-partition goroutine model.
        @KafkaListener(topics = "benchmark-topic", groupId = "spring-consumer-group")
        public void consume(ConsumerRecord<String, String> record) {
            processRecord(record.value());
        }

        private void processRecord(String message) {
            long now = System.currentTimeMillis();
            // compareAndSet(0, now) is atomic: only the very first record sets firstAt.
            firstAt.compareAndSet(0, now);

            restClient.get()
                    .uri("http://localhost:9000/echo")
                    .retrieve()
                    .toBodilessEntity();

            long count = processed.incrementAndGet();
            lastAt.set(System.currentTimeMillis());

            if (count % 100 == 0) {
                log.info("Processed " + count + " messages...");
            }
            if (count == EXPECTED_MESSAGES) {
                log.info("DONE: processed " + count + " messages in "
                        + (lastAt.get() - firstAt.get()) + "ms");
            }
        }
    }
}
