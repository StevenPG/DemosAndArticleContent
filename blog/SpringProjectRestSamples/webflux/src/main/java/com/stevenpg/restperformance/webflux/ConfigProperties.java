package com.stevenpg.restperformance.webflux;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "endpoint")
public class ConfigProperties
{
    String ok;
    String notfound;
    String api_ok;
    String api_notfound;
}
