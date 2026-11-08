plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Do Valhalla value classes work (and help) with JPA / Hibernate on JDK 28 EA?"

// Value classes (JEP 401) are a preview feature in JDK 28. There's no foojay
// download for EA builds, so point Gradle at your JDK 28 EA install:
//   ./gradlew test -Porg.gradle.java.installations.paths=/path/to/jdk-28.jdk/Contents/Home
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(28)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
}

val benchmark by tasks.registering(Test::class) {
    description = "Runs the value-vs-identity hydration benchmark."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    maxHeapSize = "2g"
    // Pass -Pbench.flags="-XX:-UseFieldFlattening" etc. to compare JVM settings.
    (project.findProperty("bench.flags") as String?)?.split(" ")?.filter { it.isNotBlank() }?.let { jvmArgs(it) }
    outputs.upToDateWhen { false }
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
    // Scenario outcomes and benchmark numbers are printed; always show them.
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}
