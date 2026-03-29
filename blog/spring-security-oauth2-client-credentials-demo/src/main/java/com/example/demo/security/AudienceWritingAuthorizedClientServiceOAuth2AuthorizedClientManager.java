package com.example.demo.security;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class AudienceWritingAuthorizedClientServiceOAuth2AuthorizedClientManager implements OAuth2AuthorizedClientManager {
    private static final OAuth2AuthorizedClientProvider DEFAULT_AUTHORIZED_CLIENT_PROVIDER = OAuth2AuthorizedClientProviderBuilder
            .builder()
            .clientCredentials()
            .build();

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private OAuth2AuthorizedClientProvider authorizedClientProvider;
    private Function<OAuth2AuthorizeRequest, Map<String, Object>> contextAttributesMapper;
    private OAuth2AuthorizationSuccessHandler authorizationSuccessHandler;
    private OAuth2AuthorizationFailureHandler authorizationFailureHandler;

    /**
     * Constructs an {@code AuthorizedClientServiceOAuth2AuthorizedClientManager} using
     * the provided parameters.
     * @param clientRegistrationRepository the repository of client registrations
     * @param authorizedClientService the authorized client service
     */
    public AudienceWritingAuthorizedClientServiceOAuth2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
        Assert.notNull(authorizedClientService, "authorizedClientService cannot be null");
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientService = authorizedClientService;
        this.authorizedClientProvider = DEFAULT_AUTHORIZED_CLIENT_PROVIDER;
        this.contextAttributesMapper = new AuthorizedClientServiceOAuth2AuthorizedClientManager.DefaultContextAttributesMapper();
        this.authorizationSuccessHandler = (authorizedClient, principal, attributes) -> authorizedClientService
                .saveAuthorizedClient(authorizedClient, principal);
        this.authorizationFailureHandler = new RemoveAuthorizedClientOAuth2AuthorizationFailureHandler(
                (clientRegistrationId, principal, attributes) -> authorizedClientService
                        .removeAuthorizedClient(clientRegistrationId, principal.getName()));
    }

    @Nullable
    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest authorizeRequest) {
        Assert.notNull(authorizeRequest, "authorizeRequest cannot be null");
        String clientRegistrationId = authorizeRequest.getClientRegistrationId();
        OAuth2AuthorizedClient authorizedClient = authorizeRequest.getAuthorizedClient();
        Authentication principal = authorizeRequest.getPrincipal();
        OAuth2AuthorizationContext.Builder contextBuilder;
        if (authorizedClient != null) {
            contextBuilder = OAuth2AuthorizationContext.withAuthorizedClient(authorizedClient);
        }
        else {
            ClientRegistration clientRegistration = this.clientRegistrationRepository
                    .findByRegistrationId(clientRegistrationId);
            Assert.notNull(clientRegistration,
                    "Could not find ClientRegistration with id '" + clientRegistrationId + "'");
            authorizedClient = this.authorizedClientService.loadAuthorizedClient(clientRegistrationId,
                    principal.getName());
            if (authorizedClient != null) {
                contextBuilder = OAuth2AuthorizationContext.withAuthorizedClient(authorizedClient);
            }
            else {
                contextBuilder = OAuth2AuthorizationContext.withClientRegistration(clientRegistration);
            }
        }
        OAuth2AuthorizationContext authorizationContext = buildAuthorizationContext(authorizeRequest, principal,
                contextBuilder);
        try {
            authorizedClient = this.authorizedClientProvider.authorize(authorizationContext);
        }
        catch (OAuth2AuthorizationException ex) {
            this.authorizationFailureHandler.onAuthorizationFailure(ex, principal, Collections.emptyMap());
            throw ex;
        }
        if (authorizedClient != null) {
            this.authorizationSuccessHandler.onAuthorizationSuccess(authorizedClient, principal,
                    Collections.emptyMap());
        }
        else {
            // In the case of re-authorization, the returned `authorizedClient` may be
            // null if re-authorization is not supported.
            // For these cases, return the provided
            // `authorizationContext.authorizedClient`.
            if (authorizationContext.getAuthorizedClient() != null) {
                return authorizationContext.getAuthorizedClient();
            }
        }
        return authorizedClient;
    }

    private OAuth2AuthorizationContext buildAuthorizationContext(OAuth2AuthorizeRequest authorizeRequest,
                                                                 Authentication principal, OAuth2AuthorizationContext.Builder contextBuilder) {
        // @formatter:off
        return contextBuilder.principal(principal)
                .attributes((attributes) -> {
                    Map<String, Object> contextAttributes = this.contextAttributesMapper.apply(authorizeRequest);
                    if (!CollectionUtils.isEmpty(contextAttributes)) {
                        attributes.putAll(contextAttributes);
                    }
                })
                .build();
        // @formatter:on
    }

    /**
     * Sets the {@link OAuth2AuthorizedClientProvider} used for authorizing (or
     * re-authorizing) an OAuth 2.0 Client.
     * @param authorizedClientProvider the {@link OAuth2AuthorizedClientProvider} used for
     * authorizing (or re-authorizing) an OAuth 2.0 Client
     */
    public void setAuthorizedClientProvider(OAuth2AuthorizedClientProvider authorizedClientProvider) {
        Assert.notNull(authorizedClientProvider, "authorizedClientProvider cannot be null");
        this.authorizedClientProvider = authorizedClientProvider;
    }

    /**
     * Sets the {@code Function} used for mapping attribute(s) from the
     * {@link OAuth2AuthorizeRequest} to a {@code Map} of attributes to be associated to
     * the {@link OAuth2AuthorizationContext#getAttributes() authorization context}.
     * @param contextAttributesMapper the {@code Function} used for supplying the
     * {@code Map} of attributes to the {@link OAuth2AuthorizationContext#getAttributes()
     * authorization context}
     */
    public void setContextAttributesMapper(
            Function<OAuth2AuthorizeRequest, Map<String, Object>> contextAttributesMapper) {
        Assert.notNull(contextAttributesMapper, "contextAttributesMapper cannot be null");
        this.contextAttributesMapper = contextAttributesMapper;
    }

}
