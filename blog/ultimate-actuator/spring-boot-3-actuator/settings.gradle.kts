// The foojay-resolver-convention plugin lets Gradle automatically download and
// provision the exact JDK declared by the Java toolchain below (Java 25), even
// when the machine running Gradle only has an older JDK installed.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "spring-boot-3-actuator"
