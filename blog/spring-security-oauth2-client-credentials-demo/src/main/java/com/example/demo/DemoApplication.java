package com.example.demo;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestClient;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

@SpringBootApplication
public class DemoApplication {

    @Autowired
    RestClient restClient;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // This method will be called after the application context is initialized
        System.out.println("Application has started successfully!");

        restClient.get()
                .attributes(clientRegistrationId("test-client")
                        .andThen(stringObjectMap ->
                                stringObjectMap.put("audience", "httpbin.org")))
                .retrieve()
                .onStatus(httpStatusCode -> {
                    if (httpStatusCode.is2xxSuccessful()) {
                        System.out.println("Received a successful response from the REST client!");
                        return true;
                    } else {
                        System.out.println("Received an invalid response: " + httpStatusCode);
                        return false;
                    }
                }, (_, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        System.out.println("Received an error response: " + response.getStatusCode());
                    }
                }).toBodilessEntity();
        System.out.println("Application has completed successfully!");
    }

}
