// ---------------------------------------------------------------------------
// gateway-webflux — the REACTIVE flavor of Spring Cloud Gateway.
//
// Runs on Netty (not Tomcat). Routes, predicates and filters are configured
// reactively; the whole request path is non-blocking. This is the original,
// most feature-complete flavor of Spring Cloud Gateway.
// ---------------------------------------------------------------------------
dependencies {
    // The gateway itself (pulls in spring-boot-starter-webflux + Netty).
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // --- Resilience: circuit breaker + retry -------------------------------
    // Reactor-flavored Resilience4j so the CircuitBreaker filter is non-blocking.
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // --- Rate limiting -----------------------------------------------------
    // The built-in RequestRateLimiter filter stores its token buckets in Redis.
    // Reactive Redis client because we are on the reactive stack.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // --- Load balancing ----------------------------------------------------
    // Client-side load balancing for lb:// routes across service instances.
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // --- Security at the edge ----------------------------------------------
    // Validate JWT access tokens at the gateway (OAuth2 resource server).
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // --- Observability -----------------------------------------------------
    // Actuator exposes the gateway endpoints (/actuator/gateway/**) and Micrometer
    // records metrics. The Brave bridge produces a span per request that you can
    // export to Zipkin/OTLP (see the observability notes in application.yml).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    // Testcontainers spins up a real Redis for the rate-limiter integration test.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
