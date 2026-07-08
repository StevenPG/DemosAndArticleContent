/*
 * =========================================================================
 *  storefront-client - REST in the front, gRPC in the back
 * =========================================================================
 *
 * This is the most common way gRPC sneaks into a system: an edge service
 * keeps serving JSON to browsers/mobile apps while calling internal
 * services over gRPC. This module exposes a small REST API on port 8080
 * and forwards everything to inventory-server over gRPC on port 9090.
 */
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Same shared contract as the server - the whole point of the
    // inventory-proto module.
    implementation(project(":inventory-proto"))

    // Spring MVC (Boot 4.x name; the old spring-boot-starter-web still
    // exists as a deprecated alias).
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Actuator gives us /actuator/health, which the demo scripts poll to
    // know when the app is up.
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Boot 4.1's first-party gRPC client starter: named channels
    // from configuration properties, client interceptors, and (optionally)
    // auto-registered stub beans. Versions come from Boot's own BOM.
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
