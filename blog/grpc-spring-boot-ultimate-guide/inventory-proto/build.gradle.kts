import com.google.protobuf.gradle.id

/*
 * =========================================================================
 *  inventory-proto - the shared gRPC CONTRACT module
 * =========================================================================
 *
 * This module contains no hand-written Java at all. It holds:
 *
 *   1. The .proto files under src/main/proto  (the source of truth)
 *   2. The Java code that protoc generates from them at build time
 *
 * Both the server and the client depend on this module, which guarantees
 * they always agree on the wire format. In larger organizations this module
 * is typically its own repository (or published to a Buf/Maven registry)
 * so that services written in ANY language can generate matching stubs.
 */
plugins {
    `java-library`

    // The protobuf plugin wires `protoc` (the protobuf compiler) into the
    // Gradle build. Every `./gradlew build` regenerates the Java sources
    // from the .proto files, so generated code is never committed to git.
    id("com.google.protobuf")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

/*
 * Versions of the protobuf compiler and the gRPC codegen plugin.
 * These match the versions Spring Boot 4.1's own BOM manages (Boot 4.1
 * imports grpc-bom and protobuf-bom), so the code generated here lines up
 * exactly with the runtime libraries the service modules resolve.
 */
val protobufVersion = "4.34.2"
val grpcVersion = "1.80.0"

dependencies {
    // Pin the gRPC/protobuf runtime for this module (and export the
    // constraints to dependents via `api(platform(...))`). The service
    // modules get the same versions from Spring Boot's dependency
    // management - these BOMs exist so the versions are stated once.
    api(platform("io.grpc:grpc-bom:$grpcVersion"))
    api(platform("com.google.protobuf:protobuf-bom:$protobufVersion"))

    // Runtime libraries the *generated* code needs:
    api("io.grpc:grpc-protobuf")            // marshals protobuf messages over gRPC
    api("io.grpc:grpc-stub")                // base classes for generated stubs
    api("com.google.protobuf:protobuf-java")// core protobuf runtime (message classes)

    // google/protobuf/*.proto well-known types (Timestamp, Empty, ...) used
    // by our schema are shipped inside protobuf-java, nothing extra needed.
}

protobuf {
    // Which protoc binary to download and run. Gradle fetches it from Maven
    // Central, so contributors never install protoc manually.
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }

    // protoc by itself only generates plain message classes. The gRPC Java
    // plugin additionally generates the service classes:
    //   InventoryServiceGrpc.InventoryServiceImplBase   (extend on the server)
    //   InventoryServiceGrpc.newBlockingStub(...)       (call from clients)
    //   InventoryServiceGrpc.newStub(...)               (async/streaming client)
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                // Run the gRPC plugin for every .proto file in this module.
                id("grpc")
            }
        }
    }
}
