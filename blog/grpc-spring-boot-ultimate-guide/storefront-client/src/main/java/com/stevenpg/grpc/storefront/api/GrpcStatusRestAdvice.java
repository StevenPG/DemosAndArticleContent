package com.stevenpg.grpc.storefront.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.grpc.StatusRuntimeException;

/**
 * Maps gRPC failures to HTTP responses at the REST edge.
 *
 * <p>Blocking stubs throw {@link StatusRuntimeException} carrying the gRPC
 * status code. This advice is the mirror image of the server's
 * GrpcExceptionHandler: the server mapped business exceptions -> gRPC
 * status, and here the edge maps gRPC status -> HTTP status. The mapping
 * below follows the conventions of the official grpc-gateway project.
 */
@RestControllerAdvice
public class GrpcStatusRestAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcStatusRestAdvice.class);

    record GrpcErrorBody(String grpcStatus, String message) {
    }

    @ExceptionHandler(StatusRuntimeException.class)
    ResponseEntity<GrpcErrorBody> handleGrpcStatus(StatusRuntimeException ex) {
        HttpStatus http = switch (ex.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;                        // 404
            case INVALID_ARGUMENT, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST; // 400
            case ALREADY_EXISTS, ABORTED -> HttpStatus.CONFLICT;           // 409
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;                // 403
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;               // 401
            case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;       // 429
            case FAILED_PRECONDITION -> HttpStatus.PRECONDITION_FAILED;    // 412
            case UNIMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED;              // 501
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;            // 503
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;          // 504
            default -> HttpStatus.INTERNAL_SERVER_ERROR;                   // 500
        };

        log.warn("gRPC call failed: {} -> HTTP {}", ex.getStatus(), http.value());

        return ResponseEntity.status(http).body(new GrpcErrorBody(
                ex.getStatus().getCode().name(),
                ex.getStatus().getDescription()));
    }
}
