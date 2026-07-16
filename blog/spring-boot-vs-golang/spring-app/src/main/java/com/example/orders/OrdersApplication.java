package com.example.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A deliberately "kitchen sink" Spring Boot 4 service used as the reference
 * point for the Spring-vs-Go comparison article. In one small order service it
 * exercises the things almost every production Spring app uses:
 *
 * <ul>
 *   <li>REST API with validation and ProblemDetail error handling</li>
 *   <li>JPA + Postgres, schema managed by Flyway</li>
 *   <li>OAuth2 resource server (inbound JWTs from Keycloak, scope-based rules)</li>
 *   <li>Two outbound OAuth2 client-credentials targets (payment + inventory)</li>
 *   <li>Kafka producer and consumer with JSON payloads</li>
 *   <li>@Scheduled background job</li>
 *   <li>@ConfigurationProperties-bound configuration</li>
 *   <li>Actuator with Prometheus metrics</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }
}
