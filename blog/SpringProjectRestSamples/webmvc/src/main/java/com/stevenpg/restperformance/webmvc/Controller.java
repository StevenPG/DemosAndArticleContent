package com.stevenpg.restperformance.webmvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller
{
    private final Service service;

    @Autowired
    public Controller(Service service) {
        this.service = service;
    }

    @GetMapping("/api/v1/integration/ok")
    ResponseEntity goodEndpoint() {
        return service.goodResponse();
    }

    @GetMapping("/api/v1/integration/notfound")
    ResponseEntity badEndpoint() {
        return service.badResponse();
    }

}
