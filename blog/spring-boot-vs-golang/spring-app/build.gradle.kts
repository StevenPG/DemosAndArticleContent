plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Fully-featured Spring Boot 4 order service (the 'before' half of the Spring vs Go comparison)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring MVC REST API. Boot 4 renamed the starter (the old
    // spring-boot-starter-web alias still exists, but this is the new name).
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Bean Validation (jakarta.validation annotations on request DTOs)
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JPA/Hibernate + Postgres, schema managed by Flyway
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 split Flyway autoconfiguration out of spring-boot-autoconfigure into its
    // own starter; flyway-core alone (no autoconfig) silently does nothing at startup.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Inbound security: this app is an OAuth2 resource server, validating
    // JWTs issued by Keycloak and enforcing scopes per endpoint.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Outbound security: OAuth2 client-credentials tokens for calls to the
    // payment and inventory services (two separate client registrations).
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    // Boot 4 moved RestClient.Builder autoconfiguration out of the web starter
    // and into its own starter (same split as Flyway above).
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // Kafka producer + @KafkaListener consumer with JSON (de)serialization.
    // Boot 4 moved KafkaTemplate/listener-container autoconfiguration into its
    // own starter (same split as Flyway and RestClient above); spring-kafka
    // alone only supplies the library, not the autoconfig.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    // JavaTimeModule for Instant/LocalDate(Time) support in Kafka JSON (de)serialization.
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Actuator + Prometheus metrics endpoint
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Generates META-INF/spring-configuration-metadata.json for @ConfigurationProperties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    buildInfo() // surfaces info.build.* through /actuator/info
}

tasks.withType<Test> {
    useJUnitPlatform()
}
