plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

description = "Kafka batch consumer that COPYs sensor readings into detached per-minute staging tables"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))

    // Web + actuator so the write path is observable (health, metrics) while it runs headless.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // COPY throughput and latency percentiles are the real health signal for an
    // ingester; scrape them at /actuator/prometheus.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Boot 4 ships Kafka as its own starter. JSON on the wire uses spring-kafka 4's
    // Jackson 3 based JacksonJsonSerializer/JacksonJsonDeserializer (configured in
    // application.yaml), which handle java.time types like Instant out of the box.
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // JdbcClient for staging-table DDL; the hot path talks to the pgjdbc CopyManager directly.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // This service owns the schema: Flyway creates the partitioned parent and its indexes.
    // Boot 4 moved Flyway autoconfiguration into its own starter.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Not runtimeOnly: the COPY writer compiles against org.postgresql.copy.CopyManager.
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
