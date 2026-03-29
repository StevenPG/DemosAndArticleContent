package com.stevenpg.restperformance.springintegration;

import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "endpoint")
public class ConfigurationProperties
{
    private String ok;
    private String notfound;
}
