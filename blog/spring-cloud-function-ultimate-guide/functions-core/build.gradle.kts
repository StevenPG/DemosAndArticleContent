// ---------------------------------------------------------------------------
// functions-core — the business logic, and NOTHING else.
//
// This module has no web server, no Kafka, no Lambda handler. It only defines
// the order/payment pipeline as plain `java.util.function` beans plus the POJOs
// they pass around. Every other module in this build depends on this one and
// merely *exposes* these beans over some surface.
//
// It IS a Spring Boot autoconfiguration citizen though: it depends on
// spring-cloud-function-context, so when it is on an app's classpath the beans
// here are discovered and registered in the FunctionCatalog automatically.
//
// It is a `java-library` (not a Spring Boot application) — it produces a plain
// reusable jar, so we import the Spring Boot BOM explicitly for managed versions
// instead of applying the Spring Boot plugin.
// ---------------------------------------------------------------------------
plugins {
    `java-library`
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.7")
    }
}

dependencies {
    // The core of Spring Cloud Function: FunctionCatalog, function composition,
    // routing, type conversion, the FunctionRegistry API. This is the ONLY SCF
    // dependency the business logic needs.
    api("org.springframework.cloud:spring-cloud-function-context")

    // Reactor, so we can offer reactive (Flux/Mono) variants of the functions
    // right alongside the imperative ones. SCF treats both uniformly.
    api("io.projectreactor:reactor-core")

    // Jackson for the POJOs. (Managed by the Boot BOM.)
    api("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("io.projectreactor:reactor-test")
}
