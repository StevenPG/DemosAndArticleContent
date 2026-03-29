package com.example.demo.security;

import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;

public class AudienceWritingClientCredentialsOAuth2AuthorizedClientProvider implements OAuth2AuthorizedClientProvider {

    private AudienceWritingOAuth2AccessTokenResponseClient accessTokenResponseClient = new AudienceWritingOAuth2AccessTokenResponseClient();

    private Duration clockSkew = Duration.ofSeconds(60);

    private Clock clock = Clock.systemUTC();

    /**
     * Attempt to authorize (or re-authorize) the
     * {@link OAuth2AuthorizationContext#getClientRegistration() client} in the provided
     * {@code context}. Returns {@code null} if authorization (or re-authorization) is not
     * supported, e.g. the client's {@link ClientRegistration#getAuthorizationGrantType()
     * authorization grant type} is not {@link AuthorizationGrantType#CLIENT_CREDENTIALS
     * client_credentials} OR the {@link OAuth2AuthorizedClient#getAccessToken() access
     * token} is not expired.
     * @param context the context that holds authorization-specific state for the client
     * @return the {@link OAuth2AuthorizedClient} or {@code null} if authorization (or
     * re-authorization) is not supported
     */
    @Override
    @Nullable
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {
        Assert.notNull(context, "context cannot be null");
        ClientRegistration clientRegistration = context.getClientRegistration();
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(clientRegistration.getAuthorizationGrantType())) {
            return null;
        }
        OAuth2AuthorizedClient authorizedClient = context.getAuthorizedClient();
        if (authorizedClient != null && !hasTokenExpired(authorizedClient.getAccessToken())) {
            // If client is already authorized but access token is NOT expired than no
            // need for re-authorization
            return null;
        }
        // As per spec, in section 4.4.3 Access Token Response
        // https://tools.ietf.org/html/rfc6749#section-4.4.3
        // A refresh token SHOULD NOT be included.
        //
        // Therefore, renewing an expired access token (re-authorization)
        // is the same as acquiring a new access token (authorization).
        OAuth2ClientCredentialsAudiencedGrantRequest clientCredentialsGrantRequest = new OAuth2ClientCredentialsAudiencedGrantRequest(
                clientRegistration, context.getAttribute("audience"));
        OAuth2AccessTokenResponse tokenResponse = getTokenResponse(clientRegistration, clientCredentialsGrantRequest);
        return new OAuth2AuthorizedClient(clientRegistration, context.getPrincipal().getName(),
                tokenResponse.getAccessToken());
    }

    private OAuth2AccessTokenResponse getTokenResponse(ClientRegistration clientRegistration,
                                                       OAuth2ClientCredentialsAudiencedGrantRequest clientCredentialsGrantRequest) {
        try {
            return this.accessTokenResponseClient.getTokenResponse(clientCredentialsGrantRequest);
        }
        catch (OAuth2AuthorizationException ex) {
            throw new ClientAuthorizationException(ex.getError(), clientRegistration.getRegistrationId(), ex);
        }
    }

    private boolean hasTokenExpired(OAuth2Token token) {
        return this.clock.instant().isAfter(token.getExpiresAt().minus(this.clockSkew));
    }

}
