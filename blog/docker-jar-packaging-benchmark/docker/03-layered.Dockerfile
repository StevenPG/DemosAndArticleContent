# syntax=docker/dockerfile:1.7
# Variant 03 - Spring Boot layered jar.
# Splits the jar along its own layers.idx so a code change only rewrites
# the ~1.4MB application layer instead of the whole 60MB jar.

# The build stage is byte-for-byte identical in every Dockerfile in this
# directory. Every variant ships the same bench-app.jar, so any difference you
# measure comes from packaging, not from the application.
#
# BUILD_IMAGE defaults to the stock Temurin JDK image. The benchmark harness
# overrides it with an image that already carries a populated Gradle cache
# (scripts/seed-builder-image.sh) and passes GRADLE_ARGS=--offline, so that
# dependency downloads neither appear in nor add jitter to the measured build
# times. Leave both alone for a normal build.
ARG BUILD_IMAGE=eclipse-temurin:25-jdk-noble

FROM ${BUILD_IMAGE} AS build
WORKDIR /workspace
ARG GRADLE_ARGS=
COPY bench-app/gradlew ./
COPY bench-app/gradle gradle
COPY bench-app/settings.gradle bench-app/build.gradle ./
# Resolve plugins and dependencies before the source is copied in: this layer
# only invalidates when the build files change.
RUN ./gradlew --no-daemon --console=plain $GRADLE_ARGS help
COPY bench-app/src src
RUN ./gradlew --no-daemon --console=plain $GRADLE_ARGS bootJar \
 && cp build/libs/bench-app.jar /workspace/app.jar

FROM eclipse-temurin:25-jdk-noble AS layers
WORKDIR /layers
COPY --from=build /workspace/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
# Order matters: slowest-changing layer first.
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
