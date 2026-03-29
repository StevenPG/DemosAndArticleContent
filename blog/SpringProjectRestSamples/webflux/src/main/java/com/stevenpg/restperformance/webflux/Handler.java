package com.stevenpg.restperformance.webflux;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class Handler {

    private final Service service;

    public Handler(Service service) {
        this.service = service;
    }

    public Mono<ServerResponse> goodEndpoint(ServerRequest r) {
        var result = service.goodEndpoint();
        return ServerResponse.ok().build();
    }

    public Mono<ServerResponse> badEndpoint(ServerRequest r) {
        var result = service.badEndpoint();
        return ServerResponse
                .ok()
                .body(result, String.class);
    }
}
