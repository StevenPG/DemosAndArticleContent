package com.example.orders.clients;

import com.example.orders.config.DownstreamProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Outbound security: two OAuth2 client-credentials registrations ("payment"
 * and "inventory", declared in application.yaml) feeding two RestClient beans.
 * The {@link OAuth2ClientHttpRequestInterceptor} fetches a token from Keycloak
 * for its registration, caches it until expiry, and attaches it as a Bearer
 * header on every request — token plumbing never appears in the client code.
 */
@Configuration
public class OAuth2ClientConfig {

    /**
     * The default OAuth2AuthorizedClientManager is bound to the servlet
     * request. Order processing runs on Kafka listener threads with no request
     * in scope, so we use the service-based manager, which works anywhere.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient paymentRestClient(RestClient.Builder builder,
                                 OAuth2AuthorizedClientManager authorizedClientManager,
                                 DownstreamProperties properties) {
        return oauth2RestClient(builder, authorizedClientManager, "payment",
                properties.payment().baseUrl());
    }

    @Bean
    RestClient inventoryRestClient(RestClient.Builder builder,
                                   OAuth2AuthorizedClientManager authorizedClientManager,
                                   DownstreamProperties properties) {
        return oauth2RestClient(builder, authorizedClientManager, "inventory",
                properties.inventory().baseUrl());
    }

    private RestClient oauth2RestClient(RestClient.Builder builder,
                                        OAuth2AuthorizedClientManager authorizedClientManager,
                                        String registrationId,
                                        String baseUrl) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        interceptor.setClientRegistrationIdResolver(request -> registrationId);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(interceptor)
                .build();
    }
}
