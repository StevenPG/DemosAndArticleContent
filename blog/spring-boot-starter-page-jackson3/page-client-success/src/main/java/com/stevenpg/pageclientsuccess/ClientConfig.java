package com.stevenpg.pageclientsuccess;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ClientConfig {

    /**
     * Build a RestClient that uses the auto-configured {@link JsonMapper} bean.
     *
     * <p>{@code RestClient.builder()} creates a fresh {@code JacksonJsonHttpMessageConverter}
     * with a brand-new {@code JsonMapper} that has no customizations. By injecting the
     * auto-configured {@code JsonMapper} here — the one that
     * {@code spring-boot-starter-page-jackson3} has already applied its {@code Page} mixin to
     * via {@code JsonMapperBuilderCustomizer} — and registering it as the JSON converter,
     * deserialization of {@code Page<T>} works correctly.</p>
     */
    @Bean
    public RestClient restClient(
            JsonMapper jsonMapper,
            @Value("${page.server.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
