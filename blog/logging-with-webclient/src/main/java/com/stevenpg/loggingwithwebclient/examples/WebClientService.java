package com.stevenpg.loggingwithwebclient.examples;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientService {

    private final WebClient jettyHttpClient;
    private final ConfigProperties configProperties;

    @Autowired
    public WebClientService(ConfigProperties configProperties, @Qualifier("jettyHttpClient") WebClient jettyHttpClient) {
        this.configProperties = configProperties;
        this.jettyHttpClient = jettyHttpClient;
    }

    public void testJettyClient() {
        jettyHttpClient.get()
                .uri(configProperties.getGetEndpoint())
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
