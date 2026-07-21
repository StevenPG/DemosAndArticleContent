// ---------------------------------------------------------------------------
// Root build file.
//
// This is a multi-module project. The root project itself produces no jar; it
// only holds configuration that every subproject shares:
//
//   * the Spring Boot + dependency-management plugins (applied, not just declared)
//   * the Spring Cloud BOM (release train "Oakwood" = 2025.1.x)
//   * the Java toolchain (Java 21)
//   * common test wiring (JUnit 5)
//
// Version matrix used throughout this demo:
//   Spring Boot   4.0.7
//   Spring Cloud  2025.1.2  (Oakwood) -> Spring Cloud Gateway 5.x
//   Java          21
//   Gradle        8.14
// ---------------------------------------------------------------------------

plugins {
    java
    id("org.springframework.boot") version "4.0.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// The Spring Cloud release train. Every subproject imports this BOM (as a Gradle
// platform) so that Spring Cloud Gateway, Resilience4j, LoadBalancer, etc. all
// resolve to the versions that were tested together against Spring Boot 4.0.
val springCloudVersion = "2025.1.2"

allprojects {
    group = "com.stevenpg.gateway"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    the<JavaPluginExtension>().apply {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        // Spring Cloud BOM as a platform. Boot's own BOM is imported automatically
        // by the Spring Boot Gradle plugin; this adds the Spring Cloud versions on
        // top so subprojects can declare Gateway/Resilience4j/LB deps unversioned.
        add("implementation", platform("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion"))
        // Testcontainers BOM (the version Spring Boot 4.0.7 was tested against) so
        // test modules can declare testcontainers artifacts without a version.
        add("testImplementation", platform("org.testcontainers:testcontainers-bom:2.0.5"))

        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
