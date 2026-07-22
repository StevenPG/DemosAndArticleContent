plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    // Generates Java classes from the .avsc files under src/main/avro at build time
    // and wires the generated sources into compileJava automatically.
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Spring Boot 4 + Kafka + Confluent Schema Registry (Avro) demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    // Confluent serializers and the Schema Registry client live in Confluent's Maven repo.
    maven { url = uri("https://packages.confluent.io/maven/") }
}

extra["confluentVersion"] = "8.0.0"
extra["avroVersion"] = "1.12.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Avro runtime + Confluent Avro serializer/deserializer and Schema Registry client.
    implementation("org.apache.avro:avro:${property("avroVersion")}")
    implementation("io.confluent:kafka-avro-serializer:${property("confluentVersion")}")
    implementation("io.confluent:kafka-schema-registry-client:${property("confluentVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Generate String-typed fields (java.lang.String) instead of the default CharSequence/Utf8,
// which keeps the demo code clean.
avro {
    stringType.set("String")
    fieldVisibility.set("PRIVATE")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
