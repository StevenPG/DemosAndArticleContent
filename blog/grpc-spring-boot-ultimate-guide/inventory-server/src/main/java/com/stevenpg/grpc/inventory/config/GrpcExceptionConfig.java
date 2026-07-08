package com.stevenpg.grpc.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stevenpg.grpc.inventory.grpc.ProductNotFoundException;

import io.grpc.Status;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/**
 * Centralized exception -> gRPC status mapping.
 *
 * <p>gRPC does not have HTTP status codes; it has its own, smaller set of
 * {@link Status.Code status codes} (NOT_FOUND, INVALID_ARGUMENT,
 * UNAVAILABLE, DEADLINE_EXCEEDED, ...) carried in trailing metadata.
 * An uncaught server exception surfaces to clients as the useless
 * {@code UNKNOWN}, so mapping business exceptions to meaningful codes is
 * as important as @RestControllerAdvice is in a REST API.
 *
 * <p>Spring gRPC applies every {@link GrpcExceptionHandler} bean to every
 * service automatically. A handler returns a StatusException for exceptions
 * it understands and {@code null} to pass the exception to the next handler.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcExceptionConfig {

    @Bean
    GrpcExceptionHandler productNotFoundHandler() {
        return exception -> {
            if (exception instanceof ProductNotFoundException notFound) {
                // The description travels to the client in the status; keep
                // it helpful but never leak internals (stack traces, SQL...).
                return Status.NOT_FOUND
                        .withDescription(notFound.getMessage())
                        .asException();
            }
            return null; // not ours - let other handlers (or UNKNOWN) apply
        };
    }

    @Bean
    GrpcExceptionHandler validationHandler() {
        return exception -> {
            if (exception instanceof IllegalArgumentException badInput) {
                return Status.INVALID_ARGUMENT
                        .withDescription(badInput.getMessage())
                        .asException();
            }
            return null;
        };
    }
}
