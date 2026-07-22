// ---------------------------------------------------------------------------
// app-web — exposes the functions-core beans over HTTP.
//
// spring-cloud-starter-function-web wires the FunctionCatalog to Spring MVC:
// every Function/Supplier/Consumer bean becomes an HTTP endpoint at `/<name>`
// with ZERO controller code. GET for suppliers, POST for functions/consumers.
//
// This module writes NO business logic. Its `src/main/java` has only a
// @SpringBootApplication class; everything it serves comes from functions-core.
// ---------------------------------------------------------------------------
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":functions-core"))

    // The web adapter: FunctionCatalog -> HTTP endpoints (pulls in spring-web/MVC).
    implementation("org.springframework.cloud:spring-cloud-starter-function-web")

    // Actuator so /actuator is available like the other guides in this repo.
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-webflux") // WebTestClient
}
