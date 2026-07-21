package com.stevenpg.gateway.webmvc.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

import java.time.Duration;

/**
 * Wires up the storage the servlet gateway's built-in rate limiter needs.
 *
 * <p>This is the single biggest under-the-hood difference from the reactive
 * gateway. The reactive {@code RequestRateLimiter} filter talks to Redis directly
 * with a Lua token-bucket script. The servlet gateway instead delegates to
 * <b>Bucket4j</b> ({@code Bucket4jFilterFunctions.rateLimit(...)}), and Bucket4j
 * looks up an {@link AsyncProxyManager} bean to decide where the buckets live.
 * Point that proxy manager at Redis and you get a distributed limit shared across
 * every gateway instance — exactly like this repo's {@code bucket4j-redis} demo.
 *
 * <p>The key detail that makes it click: the gateway hands Bucket4j a
 * <b>String</b> key (from the key resolver). So the Lettuce connection is typed
 * {@code <String, byte[]>} — String keys, binary bucket state — which makes
 * {@code builderFor(connection)} produce a {@code ProxyManager<String>}.
 */
@Configuration
public class RateLimitConfig {

    /** The raw Lettuce client. Spring Boot's Redis starter doesn't expose one, so we build it. */
    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(@Value("${spring.data.redis.host:localhost}") String host,
                                     @Value("${spring.data.redis.port:6379}") int port) {
        return RedisClient.create(RedisURI.create(host, port));
    }

    /** String keys, byte[] values — the shape Bucket4j's proxy manager stores buckets in. */
    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * The bean Bucket4j resolves via {@code getBean(AsyncProxyManager.class)}. Buckets
     * expire shortly after they would have fully refilled, so idle keys don't linger
     * in Redis forever.
     */
    @Bean
    AsyncProxyManager<String> asyncProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build()
                .asAsync();
    }

    /**
     * Decides the bucket key: the authenticated subject when present (per-user limit),
     * otherwise the client IP (per-caller limit). Referenced from the orders route.
     */
    public static String resolveKey(ServerRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return request.servletRequest().getRemoteAddr();
    }
}
