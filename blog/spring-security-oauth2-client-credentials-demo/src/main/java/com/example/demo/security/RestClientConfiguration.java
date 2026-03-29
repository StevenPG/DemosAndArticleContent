package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.HashMap;

@Configuration
public class RestClientConfiguration
{

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager (
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService
    ){
        // We create a manager using the autowired clientRegistrations from YAML and connect it to the service
        AudienceWritingAuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AudienceWritingAuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);

        // Setting the clientManager to look for a clientCredentials configuration
        authorizedClientManager.setAuthorizedClientProvider(new AudienceWritingClientCredentialsOAuth2AuthorizedClientProvider());

        // This customizer is crucial for passing RestClient attributes to the OAuth2AuthorizeRequest
        authorizedClientManager.setContextAttributesMapper(authorizeRequest -> {
            // The OAuth2AuthorizedClientInterceptor automatically copies RestClient's
            // attributes into the OAuth2AuthorizeRequest's attributes.
            // So, we just return the existing attributes.
            return new HashMap<>(authorizeRequest.getAttributes());
        });

        return authorizedClientManager;
    }

    @Bean
    public RestClient oauth2RestClient(
            OAuth2AuthorizedClientManager authorizedClientManager) {

        // We instantiate a new interceptor to load into RestClient
        AudienceWritingOAuth2ClientHttpRequestInterceptor oAuth2ClientHttpRequestInterceptor =
                new AudienceWritingOAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        // Then provide it the client registration to resolve the id from

        // From here we simply return the client with any custom configuration, and we're good to go!
        return RestClient.builder()
                .baseUrl("https://httpbin.org/headers")
                .requestInterceptor(oAuth2ClientHttpRequestInterceptor)
                .build();
    }
}
