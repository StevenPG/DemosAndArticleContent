package com.example.boot42;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ProblemDetailsConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureProblemDetails(ProblemDetailsConfigurer configurer) {
        configurer.renderUnhandledExceptions(true);
    }
}
