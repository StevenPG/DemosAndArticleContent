plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.stevenpg"
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Generates the JPA static metamodel (Flight_, Airline_, ...) at compile time.
    // Without this, every path in a Specification is an unchecked String literal.
    //
    // Note the explicit version: the Spring Boot BOM manages hibernate-core but NOT
    // hibernate-jpamodelgen, so an unversioned entry fails to resolve. Keep this pinned to
    // the same Hibernate version Boot brings in (4.1.0 -> 7.4.1.Final).
    annotationProcessor("org.hibernate.orm:hibernate-jpamodelgen:7.4.1.Final")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x renamed every module: org.testcontainers:postgresql is now
    // org.testcontainers:testcontainers-postgresql.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
