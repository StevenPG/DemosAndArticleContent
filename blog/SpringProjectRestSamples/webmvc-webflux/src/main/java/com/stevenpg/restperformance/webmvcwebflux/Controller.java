package com.stevenpg.restperformance.webmvcwebflux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class Controller
{
    private final Service service;

    @Autowired
    public Controller(Service service) {
        this.service = service;
    }

    @GetMapping("/api/v1/integration/ok")
    Mono<ResponseEntity<Void>> goodEndpoint() {
        return service.goodResponse();
    }

    @GetMapping("/api/v1/integration/notfound")
    Mono<ResponseEntity> badEndpoint() {
        return service.badResponse();
    }

}
