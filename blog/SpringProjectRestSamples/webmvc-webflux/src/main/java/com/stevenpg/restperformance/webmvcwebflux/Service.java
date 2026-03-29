package com.stevenpg.restperformance.webmvcwebflux;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class Service
{
    WebClient client;
    ConfigurationProperties configurationProperties;

    public Service(ConfigurationProperties properties) {
        client = WebClient.builder().build();
        this.configurationProperties = properties;
    }

    public Mono<ResponseEntity<Void>> goodResponse() {
        return client
                .get()
                .uri(configurationProperties.getOk())
                .retrieve()
                .toBodilessEntity();
    }

    public Mono<ResponseEntity> badResponse() {
        return client
                .get()
                .uri(configurationProperties.getNotfound())
                .exchange()
                .map(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return new ResponseEntity("Entity not found.", HttpStatus.NOT_FOUND);
                    } else {
                        return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                });
    }
}
