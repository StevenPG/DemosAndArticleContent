// ---------------------------------------------------------------------------
// app-rsocket — exposes the functions-core beans over RSocket.
//
// A note on adapters: Spring Cloud Function once shipped a dedicated
// `spring-cloud-function-rsocket` adapter, but it was NOT released for the GA
// 5.0.x line (only 5.0.0 milestones exist). Rather than pin a milestone against
// GA artifacts, this module shows the pattern you use whenever a turnkey adapter
// is missing: inject the FunctionCatalog yourself and invoke functions by name.
// It is a handful of lines and works on ANY transport — here, Spring's
// first-class RSocket support via @MessageMapping.
// ---------------------------------------------------------------------------
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":functions-core"))

    // Spring's RSocket messaging support (@MessageMapping) + an RSocket server.
    implementation("org.springframework.boot:spring-boot-starter-rsocket")

    testImplementation("io.projectreactor:reactor-test")
}
