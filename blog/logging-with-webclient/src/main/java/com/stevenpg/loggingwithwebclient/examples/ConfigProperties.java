package com.stevenpg.loggingwithwebclient.examples;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "url")
public class ConfigProperties {

    private String getEndpoint;
    private String badEndpoint;
}
