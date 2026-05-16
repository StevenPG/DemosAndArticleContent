plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.stevenpg"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Jazzer: the most widely used Java fuzzing framework.
    // @FuzzTest methods run as regression tests during `./gradlew test` (corpus replay mode).
    // Set JAZZER_FUZZ=1 or pass -Djazzer.fuzz=true to run live coverage-guided fuzzing.
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Scope Jazzer's coverage instrumentation to our own application code. This focuses
    // the coverage-guided fuzzer on the classes we care about (and avoids noise from
    // instrumenting Spring/JDK internals). Used by live fuzzing mode (JAZZER_FUZZ=1);
    // regression mode simply replays the seed corpus and does not need it.
    systemProperty("jazzer.instrument", "com.stevenpg.fuzzingdemo.**")

    // Surface Jazzer's findings clearly when a fuzz test fails.
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
