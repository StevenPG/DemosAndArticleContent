# syntax=docker/dockerfile:1.7
# Variant 04 - fully extracted jar (thin jar + lib/ directory).
# No nested-jar loader at runtime: plain classpath, plain java -jar. This
# is the layout CDS and the AOT cache need, and the prerequisite for
# variants 05, 06 and 08.

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

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=explode /app-extracted/lib lib
COPY --from=explode /app-extracted/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
