package com.example.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.client.web.client.SecurityContextHolderPrincipalResolver;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link ClientHttpRequestInterceptor} that adds an OAuth 2.0 Bearer Token to the request
 */
public class AudienceWritingOAuth2ClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    // @formatter:off
    private static final Map<HttpStatusCode, String> OAUTH2_ERROR_CODES = Map.of(
            HttpStatus.UNAUTHORIZED, OAuth2ErrorCodes.INVALID_TOKEN,
            HttpStatus.FORBIDDEN, OAuth2ErrorCodes.INSUFFICIENT_SCOPE
    );
    // @formatter:on

    private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken("anonymous",
            "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    private OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver clientRegistrationIdResolver = new RequestAttributeClientRegistrationIdResolver();

    private OAuth2ClientHttpRequestInterceptor.PrincipalResolver principalResolver = new SecurityContextHolderPrincipalResolver();

    // @formatter:off
    private OAuth2AuthorizationFailureHandler authorizationFailureHandler =
            (clientRegistrationId, principal, attributes) -> { };
    // @formatter:on

    /**
     * Constructs a {@code OAuth2ClientHttpRequestInterceptor} using the provided
     * parameters.
     * @param authorizedClientManager the {@link OAuth2AuthorizedClientManager} which
     * manages the authorized client(s)
     */
    public AudienceWritingOAuth2ClientHttpRequestInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        Assert.notNull(authorizedClientManager, "authorizedClientManager cannot be null");
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication principal = this.principalResolver.resolve(request);
        if (principal == null) {
            principal = ANONYMOUS_AUTHENTICATION;
        }

        authorizeClient(request, principal);
        try {
            ClientHttpResponse response = execution.execute(request, body);
            handleAuthorizationFailure(request, principal, response.getHeaders(), response.getStatusCode());
            return response;
        }
        catch (RestClientResponseException ex) {
            handleAuthorizationFailure(request, principal, ex.getResponseHeaders(), ex.getStatusCode());
            throw ex;
        }
        catch (OAuth2AuthorizationException ex) {
            handleAuthorizationFailure(ex, principal);
            throw ex;
        }
    }

    private void authorizeClient(HttpRequest request, Authentication principal) {
        String clientRegistrationId = this.clientRegistrationIdResolver.resolve(request);
        if (clientRegistrationId == null) {
            return;
        }

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
                .principal(principal)
                .attribute("audience", request.getAttributes().get("audience"))
                .build();
        OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient != null) {
            request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        }
    }

    private void handleAuthorizationFailure(HttpRequest request, Authentication principal, HttpHeaders headers,
                                            HttpStatusCode httpStatus) {
        OAuth2Error error = resolveOAuth2ErrorIfPossible(headers, httpStatus);
        if (error == null) {
            return;
        }

        String clientRegistrationId = this.clientRegistrationIdResolver.resolve(request);
        if (clientRegistrationId == null) {
            return;
        }

        ClientAuthorizationException authorizationException = new ClientAuthorizationException(error,
                clientRegistrationId);
        handleAuthorizationFailure(authorizationException, principal);
    }

    private static OAuth2Error resolveOAuth2ErrorIfPossible(HttpHeaders headers, HttpStatusCode httpStatus) {
        String wwwAuthenticateHeader = headers.getFirst(HttpHeaders.WWW_AUTHENTICATE);
        if (wwwAuthenticateHeader != null) {
            Map<String, String> parameters = parseWwwAuthenticateHeader(wwwAuthenticateHeader);
            if (parameters.containsKey(OAuth2ParameterNames.ERROR)) {
                return new OAuth2Error(parameters.get(OAuth2ParameterNames.ERROR),
                        parameters.get(OAuth2ParameterNames.ERROR_DESCRIPTION),
                        parameters.get(OAuth2ParameterNames.ERROR_URI));
            }
        }

        String errorCode = OAUTH2_ERROR_CODES.get(httpStatus);
        if (errorCode != null) {
            return new OAuth2Error(errorCode, null, "https://tools.ietf.org/html/rfc6750#section-3.1");
        }

        return null;
    }

    private static Map<String, String> parseWwwAuthenticateHeader(String wwwAuthenticateHeader) {
        if (!StringUtils.hasLength(wwwAuthenticateHeader)
                || !StringUtils.startsWithIgnoreCase(wwwAuthenticateHeader, "bearer")) {
            return Map.of();
        }

        String headerValue = wwwAuthenticateHeader.substring("bearer".length()).stripLeading();
        Map<String, String> parameters = new HashMap<>();
        for (String kvPair : StringUtils.delimitedListToStringArray(headerValue, ",")) {
            String[] kv = StringUtils.split(kvPair, "=");
            if (kv == null || kv.length <= 1) {
                continue;
            }

            parameters.put(kv[0].trim(), kv[1].trim().replace("\"", ""));
        }

        return parameters;
    }

    private void handleAuthorizationFailure(OAuth2AuthorizationException authorizationException,
                                            Authentication principal) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        Map<String, Object> attributes = new HashMap<>();
        if (requestAttributes != null) {
            attributes.put(HttpServletRequest.class.getName(), requestAttributes.getRequest());
            if (requestAttributes.getResponse() != null) {
                attributes.put(HttpServletResponse.class.getName(), requestAttributes.getResponse());
            }
        }

        this.authorizationFailureHandler.onAuthorizationFailure(authorizationException, principal, attributes);
    }

}
