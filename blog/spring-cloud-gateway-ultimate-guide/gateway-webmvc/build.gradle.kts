// ---------------------------------------------------------------------------
// gateway-webmvc — the SERVLET flavor of Spring Cloud Gateway.
//
// Runs on Tomcat with a blocking, servlet-based request path. Routes are built
// with the functional `RouterFunctions`-style API (GatewayRouterFunctions +
// HandlerFunctions + FilterFunctions) instead of the reactive RouteLocator.
// Same features, different engine — this module is the deliberate contrast to
// gateway-webflux.
// ---------------------------------------------------------------------------
dependencies {
    // The gateway itself (pulls in spring-boot-starter-web + Tomcat).
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")

    // --- Resilience: circuit breaker + retry -------------------------------
    // Servlet (blocking) Resilience4j — note: NOT the "reactor" variant.
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

    // --- Rate limiting -----------------------------------------------------
    // The servlet gateway's built-in rate limiter (Bucket4jFilterFunctions) is
    // backed by Bucket4j. For a DISTRIBUTED limit shared across gateway instances
    // we store the buckets in Redis via bucket4j-redis (Lettuce). This is the same
    // building block as this repo's `bucket4j-redis-rate-limiting` demo.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.bucket4j:bucket4j-redis:8.10.1")
    implementation("io.lettuce:lettuce-core")

    // --- Load balancing ----------------------------------------------------
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // --- Security at the edge ----------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // --- Observability -----------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    // Testcontainers spins up a real Redis for the rate-limiter integration test.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
