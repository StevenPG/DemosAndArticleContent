// ---------------------------------------------------------------------------
// app-stream-kafka — exposes the functions-core beans over Kafka topics.
//
// Spring Cloud Stream binds a function to input/output destinations: a
// Function<Order, Decision> named `orderPipeline` becomes
//   orders topic  -> orderPipeline -> decisions topic
// with NO Kafka API in your code. This module adds the messaging concerns that
// only make sense on a broker: a dead-letter queue for poison messages, and
// distributed tracing across the function invocation.
// ---------------------------------------------------------------------------
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":functions-core"))

    // Spring Cloud Stream + the Kafka binder. Brings spring-kafka transitively.
    implementation("org.springframework.cloud:spring-cloud-stream")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")

    // --- Observability: trace every function invocation across the stream -----
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    // --- Testcontainers: a real Kafka broker for the integration test ---------
    // Note: Testcontainers 2.x artifact ids are prefixed `testcontainers-*`.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.awaitility:awaitility")
}
