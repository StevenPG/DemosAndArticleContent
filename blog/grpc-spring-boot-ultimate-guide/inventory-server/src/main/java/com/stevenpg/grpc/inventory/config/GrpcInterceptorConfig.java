package com.stevenpg.grpc.inventory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Server-side interceptors - gRPC's equivalent of servlet filters.
 *
 * <p>Interceptors see every call before the service method runs and can
 * read/write metadata (headers/trailers), short-circuit calls, add auth,
 * metrics, tracing... The {@link GlobalServerInterceptor} annotation tells
 * Spring gRPC to apply the bean to every service on this server; without
 * it you would list interceptors per-service.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcInterceptorConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcInterceptorConfig.class);

    /**
     * Metadata keys are declared once and reused; the {@code Metadata.Key}
     * carries both the header name and how to (de)serialize it. Binary
     * headers are possible too - their names must end in "-bin".
     */
    static final Metadata.Key<String> CLIENT_ID_KEY =
            Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);

    @Bean
    @GlobalServerInterceptor
    ServerInterceptor requestLoggingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                // Full method name looks like:
                //   inventory.v1.InventoryService/GetProduct
                String method = call.getMethodDescriptor().getFullMethodName();

                // Read a custom header the storefront-client attaches via its
                // own CLIENT interceptor - the two ends of the same feature.
                String clientId = headers.get(CLIENT_ID_KEY);

                long startNanos = System.nanoTime();

                // Wrap the call so we can observe its completion status.
                ServerCall<ReqT, RespT> timedCall = new SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
                        log.info("gRPC {} from client [{}] -> {} in {} ms",
                                method,
                                clientId != null ? clientId : "anonymous",
                                status.getCode(),
                                elapsedMillis);
                        super.close(status, trailers);
                    }
                };

                return next.startCall(timedCall, headers);
            }
        };
    }
}
