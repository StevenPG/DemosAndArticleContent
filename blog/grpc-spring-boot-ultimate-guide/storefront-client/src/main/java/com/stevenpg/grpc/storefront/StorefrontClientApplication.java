package com.stevenpg.grpc.storefront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the storefront REST-to-gRPC gateway.
 *
 * <p>Runs a normal Spring MVC server on port 8080. All outbound calls to
 * inventory-server go over a managed gRPC channel - see
 * {@link com.stevenpg.grpc.storefront.config.GrpcClientConfig}.
 */
@SpringBootApplication
public class StorefrontClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorefrontClientApplication.class, args);
    }
}
