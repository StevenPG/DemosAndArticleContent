package com.stevenpg.grpc.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the inventory gRPC server.
 *
 * <p>There is nothing gRPC-specific here - that is the point. Spring Boot
 * 4.1's spring-boot-starter-grpc-server autoconfigures a Netty gRPC server
 * and registers every Spring bean that implements
 * {@link io.grpc.BindableService} (which every generated {@code *ImplBase}
 * class does). See {@link com.stevenpg.grpc.inventory.grpc.InventoryGrpcService}.
 */
@SpringBootApplication
public class InventoryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServerApplication.class, args);
    }
}
