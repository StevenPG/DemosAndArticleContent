package com.stevenpg.grpc.storefront.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Client-side interceptor: attaches metadata to every outbound call.
 *
 * <p>Metadata is gRPC's header mechanism - the standard carrier for auth
 * tokens ({@code authorization: Bearer ...}), correlation IDs, and tenant
 * info. This one sends a static client identifier which the server's
 * logging interceptor reads back out; swap in a real token supplier and
 * this becomes your auth plumbing.
 *
 * <p>{@code @GlobalClientInterceptor} applies it to every channel the
 * {@link org.springframework.grpc.client.GrpcChannelFactory} creates.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcClientInterceptorConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientInterceptorConfig.class);

    private static final Metadata.Key<String> CLIENT_ID_KEY =
            Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);

    @Bean
    @GlobalClientInterceptor
    ClientInterceptor clientIdInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {

                return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(CLIENT_ID_KEY, "storefront-client");
                        log.debug("gRPC -> {}", method.getFullMethodName());
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }
}
