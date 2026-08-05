plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

description = "Spring Data JPA saveAll() baseline writing to a single flat table, for comparison against the COPY path"

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

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // The whole point of this module: the ordinary Spring Data JPA write path.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Owns its own flat schema, tracked in a separate history table so it can
    // share a database with the ingest service's partitioned schema.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
