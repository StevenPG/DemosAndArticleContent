/*
 * Root build file.
 *
 * Nothing is built at the root level - it only pins shared coordinates so
 * every module resolves the same versions. The interesting build logic
 * lives in each module's own build.gradle.kts:
 *
 *   inventory-proto/build.gradle.kts   - protobuf/gRPC code generation
 *   inventory-server/build.gradle.kts  - Spring Boot gRPC server
 *   storefront-client/build.gradle.kts - Spring Boot REST -> gRPC client
 */
plugins {
    // Declare the Spring Boot plugin here (apply false) so the version is
    // defined exactly once; the service modules apply it for real.
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.5" apply false
}

allprojects {
    group = "com.stevenpg.grpc"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
