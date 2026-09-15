# syntax=docker/dockerfile:1.7
# Variant 05 - extracted jar + AppCDS archive.
# Trains a class-data-sharing archive at BUILD time by starting the app
# once with spring.context.exit=onRefresh, then maps that archive at
# runtime. Costs image size, buys startup time.

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

FROM eclipse-temurin:25-jdk-noble AS explode
WORKDIR /explode
COPY --from=build /workspace/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination /app-extracted

# The training run has to happen on the same JVM, from the same absolute paths
# the container will use at runtime - a CDS archive records the classpath it was
# built from and is silently ignored if it does not match.
FROM eclipse-temurin:25-jre-noble AS train
WORKDIR /app
COPY --from=explode /app-extracted/lib lib
COPY --from=explode /app-extracted/app.jar app.jar
RUN java -XX:ArchiveClassesAtExit=/app/app.jsa \
        -Dspring.context.exit=onRefresh \
        -Dbench.seed.rows=0 \
        -jar /app/app.jar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=train /app /app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:SharedArchiveFile=/app/app.jsa", "-jar", "/app/app.jar"]
