/*
 * =========================================================================
 *  inventory-server - a PURE gRPC Spring Boot service
 * =========================================================================
 *
 * Note what is absent: no spring-boot-starter-webmvc, no Tomcat. This
 * service speaks only gRPC. Spring Boot boots a Netty-based gRPC server on
 * port 9090 (configurable via spring.grpc.server.port).
 *
 * NEW IN SPRING BOOT 4.1: gRPC support is a first-party Spring Boot
 * feature. The starters live under org.springframework.boot
 * (spring-boot-starter-grpc-server / -client), built on the Spring gRPC
 * project's core (org.springframework.grpc:spring-grpc-core), and Boot's
 * own BOM manages every io.grpc and protobuf version. Before 4.1 you wired
 * the standalone Spring gRPC starters (or third-party ones) yourself.
 *
 * If you DO add a web starter alongside, the gRPC services can instead be
 * mounted on the servlet container so HTTP and gRPC share one port
 * (spring.grpc.server.servlet.enabled). We keep them separate here because
 * it is the more common production topology.
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
    // The shared contract: generated messages + service base classes.
    implementation(project(":inventory-proto"))

    // Spring Boot 4.1's first-party gRPC server starter: discovers every
    // BindableService bean, builds the Netty server, applies interceptors
    // and exception handlers, and registers the standard reflection and
    // health services (it brings io.grpc:grpc-services along). All
    // versions are managed by Boot's own BOM - no extra platform needed.
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")

    // Boot 4.1's gRPC test starter: @AutoConfigureTestGrpcTransport swaps
    // the Netty server for gRPC's in-process transport so integration
    // tests exercise the full stack (marshalling, interceptors, exception
    // handlers) without opening a real port. Includes
    // spring-boot-starter-test transitively.
    testImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
