plugins {
    java
    // Spring AI 1.1.x targets Spring Boot 3.5.x; move to Boot 4 once
    // Spring AI ships its Boot-4 line as GA.
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "RAG chatbot over blog posts using Spring AI + Ollama, fully local"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Chat + embeddings against a local Ollama server
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    // QuestionAnswerAdvisor (the RAG glue)
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
