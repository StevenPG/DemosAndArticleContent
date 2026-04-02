package com.stevenpg.pageclientfailure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    /**
     * Build a RestClient using the auto-configured {@link RestClient.Builder} bean.
     *
     * <p>Spring Boot's {@code RestClientAutoConfiguration} provides a prototype-scoped
     * {@code RestClient.Builder} that is pre-configured with {@code HttpMessageConverters},
     * including a {@code JacksonJsonHttpMessageConverter} backed by the auto-configured
     * {@code JsonMapper}. However, because {@code spring-boot-starter-page-jackson3} is
     * <strong>not</strong> on the classpath, no {@code Page} mixin is registered — so
     * {@code Page<T>} deserialization still fails at runtime regardless of which builder
     * factory is used.</p>
     *
     * <p>Using the static {@code RestClient.builder()} factory instead would create a fresh
     * builder with a plain {@code JsonMapper} and no mixin — the same observable outcome.</p>
     */
    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            @Value("${page.server.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
