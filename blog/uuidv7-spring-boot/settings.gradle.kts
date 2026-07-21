// The foojay-resolver-convention plugin lets Gradle automatically download and
// provision the exact JDK declared by the Java toolchain, even when the machine
// running Gradle only has an older JDK installed.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "uuidv7-spring-boot"
