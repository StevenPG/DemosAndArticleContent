package com.stevenpg.restperformance.webflux;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class Service
{
    private final WebClient webClient;
    private final ConfigProperties configProperties;

    public Service(WebClient webClient, ConfigProperties configurationProperties) {
        this.webClient = webClient;
        this.configProperties = configurationProperties;
    }

    public Mono<String> goodEndpoint() {
        return webClient
                .get()
                .uri(configProperties.getNotfound())
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> badEndpoint() {
        return webClient
                .get()
                .uri(configProperties.getNotfound())
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse -> {
                    if(clientResponse.statusCode().equals(HttpStatus.NOT_FOUND)){
                        return Mono.error(new HttpClientErrorException(HttpStatus.NOT_FOUND,
                                "Entity not found."));
                    } else {
                        return Mono.error(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
                    }
                })
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just(e.getMessage()));
    }
}
