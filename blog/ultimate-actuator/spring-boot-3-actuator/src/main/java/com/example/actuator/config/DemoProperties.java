package com.example.actuator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Custom {@link ConfigurationProperties}. These appear in the {@code configprops}
 * actuator endpoint (with sensitive values sanitized) and, thanks to the
 * configuration-processor on the annotation path, get IDE auto-completion.
 *
 * @param greeting  the message returned by the demo greeting endpoint
 * @param featureX  toggles an example feature flag exposed via the custom endpoint
 * @param apiKey    a deliberately "sensitive" value to show configprops sanitization
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
        String greeting,
        boolean featureX,
        String apiKey) {

    public DemoProperties {
        if (greeting == null) {
            greeting = "Hello from the Ultimate Actuator demo!";
        }
    }
}
