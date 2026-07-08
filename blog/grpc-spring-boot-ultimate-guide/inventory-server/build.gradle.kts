/*
 * =========================================================================
 *  inventory-server - a PURE gRPC Spring Boot service
 * =========================================================================
 *
 * Note what is absent: no spring-boot-starter-webmvc, no Tomcat. This
 * service speaks only gRPC. Spring gRPC boots a Netty-based gRPC server on
 * port 9090 (configurable via spring.grpc.server.port).
 *
 * If you DO add a web starter alongside spring-grpc, Spring gRPC can
 * instead mount the gRPC services on the servlet container so HTTP and
 * gRPC share one port - see the spring-grpc-server-web-spring-boot-starter
 * artifact. We keep them separate here because it is the more common
 * production topology.
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
    // Pins every io.grpc/protobuf artifact to versions tested with Spring gRPC.
    implementation(platform("org.springframework.grpc:spring-grpc-dependencies:1.0.3"))

    // The shared contract: generated messages + service base classes.
    implementation(project(":inventory-proto"))

    // Spring gRPC autoconfiguration: discovers every BindableService bean,
    // builds the Netty server, applies interceptors/exception handlers,
    // wires health + reflection services, etc.
    implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")

    // grpc-services contributes the standard reflection service (used by
    // grpcurl/Postman to discover the API at runtime) and the standard
    // health service (grpc.health.v1.Health). Spring gRPC auto-registers
    // both when this artifact is on the classpath.
    implementation("io.grpc:grpc-services")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Spring gRPC test support: @AutoConfigureInProcessTransport swaps the
    // Netty server for gRPC's in-process transport so integration tests
    // exercise the full stack (marshalling, interceptors, exception
    // handlers) without opening a real port.
    testImplementation("org.springframework.grpc:spring-grpc-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
