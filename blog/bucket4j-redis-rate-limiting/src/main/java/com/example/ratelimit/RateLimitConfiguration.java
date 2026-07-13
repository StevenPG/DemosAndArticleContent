package com.example.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class RateLimitConfiguration implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public RateLimitConfiguration(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Bean(destroyMethod = "shutdown")
    RedisClient redisClient(@Value("${rate-limit.redis-uri}") String redisUri) {
        return RedisClient.create(redisUri);
    }

    /**
     * The proxy manager stores bucket state in Redis with compare-and-swap, so
     * every app instance enforces the same limits. Keys expire shortly after a
     * bucket refills, keeping Redis tidy.
     */
    @Bean
    LettuceBasedProxyManager<byte[]> proxyManager(RedisClient redisClient) {
        return LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
