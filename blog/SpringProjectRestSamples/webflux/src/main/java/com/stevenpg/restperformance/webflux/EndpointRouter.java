package com.stevenpg.restperformance.webflux;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class EndpointRouter
{

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    public RouterFunction<ServerResponse> goodRoute(Handler handler, ConfigProperties configProperties) {
        log.info(configProperties.toString());
        return
                RouterFunctions.route(RequestPredicates.GET(configProperties.getApi_ok()),
                        handler::goodEndpoint)
        .and(
                RouterFunctions.route(RequestPredicates.GET(configProperties.getApi_notfound()),
                        handler::badEndpoint));
    }
}
