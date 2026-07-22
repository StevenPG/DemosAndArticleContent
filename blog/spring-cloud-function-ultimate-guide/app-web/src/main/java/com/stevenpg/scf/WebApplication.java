package com.stevenpg.scf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The HTTP surface.
 *
 * <p>There is deliberately no controller and no function here. Because this app
 * sits in the {@code com.stevenpg.scf} base package, component scanning picks up
 * every {@code @Configuration}/{@code @Component} from {@code functions-core},
 * and {@code spring-cloud-function-web} publishes each function bean as an HTTP
 * endpoint automatically:
 *
 * <pre>
 *   GET  /generateOrders                      (a Supplier)
 *   POST /enrichOrder                          (a Function)
 *   POST /enrichOrder,validateOrder            (composed on the fly)
 *   POST /notify                               (a Consumer -> 202 Accepted)
 *   POST /functionRouter  + routing headers    (routing)
 * </pre>
 *
 * See {@code application.yml} and the README for the full endpoint tour.
 */
@SpringBootApplication
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
