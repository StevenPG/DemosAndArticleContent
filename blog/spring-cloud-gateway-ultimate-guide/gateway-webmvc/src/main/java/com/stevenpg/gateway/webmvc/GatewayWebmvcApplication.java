package com.stevenpg.gateway.webmvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SERVLET gateway. See {@code RoutesConfig} for the functional
 * route definitions — the servlet gateway's headline difference from the reactive
 * one is that routes are {@code RouterFunction<ServerResponse>} beans, not YAML.
 */
@SpringBootApplication
public class GatewayWebmvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayWebmvcApplication.class, args);
    }
}
