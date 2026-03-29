package com.example.demo.security;

import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Represents a grant request for the OAuth 2.0 Client Credentials flow with an audience.
 * This class extends the standard OAuth2ClientCredentialsGrantRequest to include an audience parameter.
 */
public class OAuth2ClientCredentialsAudiencedGrantRequest extends OAuth2ClientCredentialsGrantRequest {

    private final String audience;

    public OAuth2ClientCredentialsAudiencedGrantRequest(ClientRegistration clientRegistration, String audience) {
        super(clientRegistration);
        this.audience = audience;
    }

    public String getAudience() {
        return audience;
    }
}
