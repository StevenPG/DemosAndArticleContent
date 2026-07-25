// ---------------------------------------------------------------------------
// adapter-aws — packages the functions-core beans as an AWS Lambda.
//
// spring-cloud-function-adapter-aws provides `FunctionInvoker`, an AWS
// RequestStreamHandler that boots this Spring app and routes the Lambda's
// input stream to the function named by `spring.cloud.function.definition`.
//
// LOCAL-ONLY: this guide does not deploy to a real AWS account. The test below
// drives the ACTUAL FunctionInvoker handler in-process (input stream -> output
// stream), which is exactly what Lambda does at runtime — so you can prove the
// handler works with no cloud account. The README shows the `sam local invoke`
// and packaging steps for when you do want to ship it.
// ---------------------------------------------------------------------------
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":functions-core"))

    // The AWS adapter: FunctionInvoker (a Lambda RequestStreamHandler).
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws")

    // AWS Lambda Java runtime interfaces (RequestStreamHandler, Context).
    // Not managed by the Spring Cloud BOM, so we pin it.
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")

    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

// For a REAL deployment you would shade this into a single "uber" jar (the AWS
// custom-runtime layout), e.g. with the Shadow plugin, because Lambda cannot
// read Spring Boot's nested-jar format. That is intentionally left out here to
// keep the guide's build lightweight — see the README's AWS section.
