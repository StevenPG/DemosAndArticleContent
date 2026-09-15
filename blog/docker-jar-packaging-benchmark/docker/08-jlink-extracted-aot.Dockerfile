# syntax=docker/dockerfile:1.7
# Variant 08 - the kitchen sink: jlink runtime + extracted jar + AOT cache.
# Every trick in this directory stacked on top of each other. Note the AOT
# cache has to be trained with the jlink runtime, not the build JDK - a
# cache produced by a different java binary is rejected at startup.

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

FROM eclipse-temurin:25-jdk-noble AS jlink
WORKDIR /jlink
COPY --from=build /workspace/app.jar app.jar
RUN jar xf app.jar \
 && jdeps --ignore-missing-deps -q \
      --recursive \
      --multi-release 25 \
      --print-module-deps \
      --class-path 'BOOT-INF/lib/*' \
      app.jar > /jlink/deps.txt
RUN jlink \
      --add-modules "$(cat /jlink/deps.txt),jdk.crypto.ec,jdk.management,jdk.localedata" \
      --include-locales=en \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /javaruntime
RUN java -Djarmode=tools -jar app.jar extract --destination /app-extracted

FROM debian:trixie-slim AS train
ENV JAVA_HOME=/opt/java
COPY --from=jlink /javaruntime ${JAVA_HOME}
WORKDIR /app
COPY --from=jlink /app-extracted/lib lib
COPY --from=jlink /app-extracted/app.jar app.jar
RUN /opt/java/bin/java -XX:AOTCacheOutput=/app/app.aot \
        -Dspring.context.exit=onRefresh \
        -Dbench.seed.rows=0 \
        -jar /app/app.jar

FROM debian:trixie-slim
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jlink /javaruntime ${JAVA_HOME}
WORKDIR /app
COPY --from=train /app /app
EXPOSE 8080
ENTRYPOINT ["/opt/java/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:AOTCache=/app/app.aot", "-jar", "/app/app.jar"]
