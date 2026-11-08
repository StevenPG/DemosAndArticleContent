package com.example.boot42;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LegacyClient {
    private final RestTemplate restTemplate;

    public LegacyClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }
}
