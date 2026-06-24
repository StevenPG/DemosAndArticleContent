plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    // CycloneDX SBOM generation. Spring Boot 4's Gradle plugin auto-detects this
    // plugin, runs the `cyclonedxBom` task, embeds the result in the jar at
    // META-INF/sbom/application.cdx.json, and the actuator `sbom` endpoint serves it.
    // Spring Boot 4 integrates with the newer CycloneDX 3.x plugin API.
    id("org.cyclonedx.bom") version "3.2.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "The Ultimate Spring Boot 4 Actuator demo"

java {
    toolchain {
        // Demo targets Java 25. Gradle will auto-provision a JDK 25 via the
        // foojay resolver (see settings.gradle.kts) if one is not found locally.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Core web app: gives us controllers/endpoints that produce real metrics & traces ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- THE star of the show: Spring Boot Actuator ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Securing actuator endpoints (custom-info + security feature) ---
    implementation("org.springframework.boot:spring-boot-starter-security")

    // --- Persistence: powers the JPA/DataSource/Flyway health indicators & metrics ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Caching: powers the `caches` actuator endpoint and cache metrics ---
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // --- Metrics export: Prometheus registry => /actuator/prometheus scrape endpoint ---
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- Distributed tracing: Micrometer Tracing bridge to OpenTelemetry + OTLP export ---
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // --- Observation AOP: enables the @Observed/@Timed/@Counted aspects ---
    // Spring Boot 4 REMOVED the `spring-boot-starter-aop` starter. Pull
    // spring-aspects (which brings spring-aop + aspectjweaver) directly instead.
    implementation("org.springframework:spring-aspects")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Generates META-INF/spring-configuration-metadata.json for our @ConfigurationProperties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    // Spring Boot 4 modularized the test slices: MockMvc auto-configuration
    // (@AutoConfigureMockMvc, @WebMvcTest, ...) now lives in its own starter.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Spring Boot 4 ships Testcontainers 2.x, whose module artifacts are renamed
    // (testcontainers-junit-jupiter / testcontainers-postgresql vs the old
    // junit-jupiter / postgresql in the Testcontainers 1.x used by Spring Boot 3).
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

springBoot {
    buildInfo() // Generates META-INF/build-info.properties => `info.build.*` in /actuator/info
}

tasks.withType<Test> {
    useJUnitPlatform()
}
