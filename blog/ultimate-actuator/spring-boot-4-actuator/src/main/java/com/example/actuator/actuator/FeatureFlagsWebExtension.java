package com.example.actuator.actuator;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * An {@link EndpointWebExtension} layered on top of {@link FeatureFlagsEndpoint}.
 *
 * <p>Extensions let you add <em>technology-specific</em> behaviour to an existing
 * endpoint without changing the endpoint itself. Here we wrap the read operation in
 * a {@link WebEndpointResponse} so that querying an unknown flag returns a proper
 * HTTP {@code 404} instead of {@code {"enabled": false}}.
 */
@Component
@EndpointWebExtension(endpoint = FeatureFlagsEndpoint.class)
public class FeatureFlagsWebExtension {

    private final FeatureFlagsEndpoint delegate;

    public FeatureFlagsWebExtension(FeatureFlagsEndpoint delegate) {
        this.delegate = delegate;
    }

    @ReadOperation
    public WebEndpointResponse<Map<String, Object>> flagWithHttpStatus(@Selector String name) {
        Map<String, Object> result = delegate.flag(name);
        boolean known = delegate.allFlags().containsKey(name);
        int status = known ? WebEndpointResponse.STATUS_OK : WebEndpointResponse.STATUS_NOT_FOUND;
        return new WebEndpointResponse<>(result, status);
    }
}
