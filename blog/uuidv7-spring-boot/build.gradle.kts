plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "UUIDv4 vs UUIDv7 vs bigint primary key benchmark for PostgreSQL 18"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Application-side UUIDv7 generation for the plain-JDBC benchmark.
    // (JPA entities use Hibernate's built-in @UuidGenerator VERSION_7 instead.)
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Benchmark output is the point of this repo — always show it.
    testLogging {
        showStandardStreams = true
    }
}
