package com.example.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class AudienceWritingOAuth2AccessTokenResponseClient implements
        OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsAudiencedGrantRequest> {

    private final Logger logger = LoggerFactory.getLogger(AudienceWritingOAuth2AccessTokenResponseClient.class);
    private static final String INVALID_TOKEN_RESPONSE_ERROR_CODE = "invalid_token_response";

    // @formatter:off
    private final RestClient restClient = RestClient.builder()
            .messageConverters((messageConverters) -> {
                messageConverters.clear();
                messageConverters.add(new FormHttpMessageConverter());
                messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .requestInterceptor((request,body,execution)-> {
                // This interceptor is not used in this implementation, but can be customized if needed
                logger.info("Request URI: {}", request.getURI());
                return execution.execute(request, body);
            })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build();
    // @formatter:on

    private final Converter<OAuth2ClientCredentialsAudiencedGrantRequest, HttpHeaders> headersConverter = new DefaultOAuth2TokenRequestHeadersConverter<>();

    private final Converter<OAuth2ClientCredentialsAudiencedGrantRequest, MultiValueMap<String, String>> parametersConverter = new DefaultOAuth2TokenRequestParametersConverter<>();

    private final Consumer<MultiValueMap<String, String>> parametersCustomizer = (parameters) -> {
    };

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2ClientCredentialsAudiencedGrantRequest grantRequest) {
        Assert.notNull(grantRequest, "grantRequest cannot be null");
        try {
            // @formatter:off
            OAuth2AccessTokenResponse accessTokenResponse = this.validatingPopulateRequest(grantRequest, grantRequest.getAudience())
                    .retrieve()
                    .body(OAuth2AccessTokenResponse.class);
            // @formatter:on
            if (accessTokenResponse == null) {
                OAuth2Error error = new OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE,
                        "Empty OAuth 2.0 Access Token Response", null);
                throw new OAuth2AuthorizationException(error);
            }
            return accessTokenResponse;
        } catch (RestClientException ex) {
            OAuth2Error error = new OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE,
                    "An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response: "
                            + ex.getMessage(),
                    null);
            throw new OAuth2AuthorizationException(error, ex);
        }
    }

    private RestClient.RequestHeadersSpec<?> validatingPopulateRequest(OAuth2ClientCredentialsAudiencedGrantRequest grantRequest,
                                                                       String audience) {
        validateClientAuthenticationMethod(grantRequest);
        return populateRequest(grantRequest, audience);
    }

    private void validateClientAuthenticationMethod(OAuth2ClientCredentialsGrantRequest grantRequest) {
        ClientRegistration clientRegistration = grantRequest.getClientRegistration();
        ClientAuthenticationMethod clientAuthenticationMethod = clientRegistration.getClientAuthenticationMethod();
        boolean supportedClientAuthenticationMethod = clientAuthenticationMethod.equals(ClientAuthenticationMethod.NONE)
                || clientAuthenticationMethod.equals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                || clientAuthenticationMethod.equals(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        if (!supportedClientAuthenticationMethod) {
            throw new IllegalArgumentException(String.format(
                    "This class supports `client_secret_basic`, `client_secret_post`, and `none` by default. Client [%s] is using [%s] instead. Please use a supported client authentication method, or use `set/addParametersConverter` or `set/addHeadersConverter` to supply an instance that supports [%s].",
                    clientRegistration.getRegistrationId(), clientAuthenticationMethod, clientAuthenticationMethod));
        }
    }

    private RestClient.RequestHeadersSpec<?> populateRequest(OAuth2ClientCredentialsAudiencedGrantRequest grantRequest,
                                                             String audience) {
        MultiValueMap<String, String> parameters = this.parametersConverter.convert(grantRequest);
        if (parameters == null) {
            parameters = new LinkedMultiValueMap<>();
        }
        this.parametersCustomizer.accept(parameters);

        var tokenRequestUri = grantRequest.getClientRegistration().getProviderDetails().getTokenUri()
                + (audience != null ? "?audience=" + audience : "");;

        return this.restClient.post()
                .uri(tokenRequestUri)
                .headers((headers) -> {
                    HttpHeaders headersToAdd = this.headersConverter.convert(grantRequest);
                    if (headersToAdd != null) {
                        headers.addAll(headersToAdd);
                    }
                })
                .body(parameters);
    }

}
