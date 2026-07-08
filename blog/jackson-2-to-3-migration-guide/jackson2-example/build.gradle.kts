/*
 * =========================================================================
 *  jackson2-example - the "BEFORE" project (Jackson 2.x, com.fasterxml)
 * =========================================================================
 *
 * Spring Boot 3.x is the last major Boot line to ship Jackson 2 as the
 * default JSON engine. This module intentionally targets Boot 3.5 (NOT 4.x)
 * so that every dependency, package, and API you see below is exactly what
 * a typical pre-migration Spring Boot codebase looks like today.
 *
 * Compare every file here 1:1 with the same-named file in
 * ../jackson3-example - that pairing IS the migration guide.
 */
plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.stevenpg.jackson2example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    /*
     * These two datatype modules are ADD-ONS in Jackson 2 - separate jars,
     * separate Maven coordinates, and (as ObjectMapperConfig.java shows)
     * something you must explicitly register on the ObjectMapper.
     *
     * jackson-datatype-jsr310  : java.time.* support (Instant, LocalDate, ...)
     * jackson-datatype-jdk8    : java.util.Optional support
     *
     * (Spring Boot's autoconfigured ObjectMapper actually detects and
     * registers these for you via findModulesViaServiceLoader() - but this
     * project builds its OWN ObjectMapper bean on purpose, so the
     * differences that Jackson 3 removes are visible in the code instead
     * of hidden behind Boot magic.)
     */
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
