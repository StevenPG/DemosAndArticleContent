/*
 * =========================================================================
 *  jackson3-example - the "AFTER" project (Jackson 3.x, tools.jackson)
 * =========================================================================
 *
 * Spring Boot 4.x is the first Boot line to default to Jackson 3. Compare
 * this file 1:1 with ../jackson2-example/build.gradle.kts.
 *
 * What changed:
 *   - org.springframework.boot:spring-boot-starter-web  -- SAME artifact,
 *     but it now pulls in spring-boot-starter-jackson (Jackson 3) instead
 *     of spring-boot-starter-json (Jackson 2) transitively.
 *   - jackson-datatype-jsr310 and jackson-datatype-jdk8 are GONE. java.time
 *     and java.util.Optional support are merged into jackson-databind
 *     itself in Jackson 3 - see ObjectMapperConfig.java for what that means
 *     for the code, not just the build file.
 *   - jackson-annotations is NOT listed here either - it comes in
 *     transitively at the SAME com.fasterxml.jackson.core:jackson-annotations
 *     coordinate it always has. Annotations did not move to tools.jackson.
 */
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.stevenpg.jackson3example"
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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
